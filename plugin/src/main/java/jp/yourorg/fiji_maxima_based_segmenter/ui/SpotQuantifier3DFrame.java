package jp.yourorg.fiji_maxima_based_segmenter.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.alg.*;
import jp.yourorg.fiji_maxima_based_segmenter.core.ThresholdModel;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter3D;
import jp.yourorg.fiji_maxima_based_segmenter.util.CsvExporter;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GUI frame for Spot_Quantifier_3D_.
 *
 * Layout:
 *   [Histogram (full 3D stack)]
 *   Threshold:  [slider] [field]
 *   [✓] Min vol µm³: [slider] [field]
 *   [✓] Max vol µm³: [slider] [field]
 *   [□] Gaussian blur  XY:[field] Z:[field]
 *   Preview: ○ Off  ● Overlay
 *   [Apply]  [Add ROI]  [Save ROI]  [Save CSV & Params]
 *
 * Preview is color-coded:
 *   yellow (50% opacity) = valid spot
 *   red                  = too small (< min vol)
 *   blue                 = too large (> max vol)
 */
public class SpotQuantifier3DFrame extends PlugInFrame {

    // --- Colors for preview overlay (ARGB) ---
    private static final int COLOR_VALID     = 0x80FFFF00; // yellow, 50% alpha
    private static final int COLOR_TOO_SMALL = 0x80FF4444; // red,    50% alpha
    private static final int COLOR_TOO_LARGE = 0x804444FF; // blue,   50% alpha

    private final ImagePlus imp;
    private final ThresholdModel model; // used only for histogram panel / tBg

    // Calibration (µm per pixel)
    private final double vw, vh, vd, voxelVol;

    // --- UI components ---
    private final HistogramPanel histogramPanel;

    private final Scrollbar threshBar;
    private final TextField threshField;

    private final Checkbox  minVolCheck;
    private final Scrollbar minVolBar;
    private final TextField minVolField;

    private final Checkbox  maxVolCheck;
    private final Scrollbar maxVolBar;
    private final TextField maxVolField;

    private final Checkbox gaussCheck;
    private final TextField gaussXYField;
    private final TextField gaussZField;

    private final Choice   connectivityChoice;
    private final Checkbox fillHolesCheck;

    private final CheckboxGroup previewGroup = new CheckboxGroup();
    private final Checkbox previewOff;
    private final Checkbox previewOverlay;
    private final Checkbox previewRoi;

    private final Button applyBtn      = new Button("Apply");
    private final Button addRoiBtn     = new Button("Add ROI");
    private final Button saveRoiBtn    = new Button("Save ROI");
    private final Button saveCsvBtn    = new Button("Save CSV");
    private final Button saveAllBtn    = new Button("Save All");

    // --- State ---
    private boolean syncing = false;

    // Current parameter values (read from UI)
    private int    threshold;
    private boolean minVolEnabled, maxVolEnabled;
    private double  minVolVal, maxVolVal;
    private boolean gaussEnabled;
    private double  gaussXYVal, gaussZVal;

    // --- Cache ---
    // CC cache: keyed on "threshold:gaussEnabled:gaussXY:gaussZ"
    private CcResult3D cachedCc;
    private String     ccCacheKey;

    // Last slider ranges for vol (set after first CC run)
    private double volRangeMin = 0.01;
    private double volRangeMax = 100.0;
    private static final int VOL_SLIDER_STEPS = 1000;

    // --- Preview / Z-watch timers ---
    private final Timer previewTimer = new Timer("sq3d-preview", true);
    private TimerTask   previewTask;
    private final AtomicInteger previewGen = new AtomicInteger();
    private final Timer zWatchTimer = new Timer("sq3d-zwatch", true);
    private int lastZ = -1;

    public SpotQuantifier3DFrame(ImagePlus imp) {
        super("Spot Quantifier 3D");
        this.imp = imp;

        Calibration cal = imp.getCalibration();
        vw       = cal.pixelWidth  > 0 ? cal.pixelWidth  : 1;
        vh       = cal.pixelHeight > 0 ? cal.pixelHeight : 1;
        vd       = cal.pixelDepth  > 0 ? cal.pixelDepth  : 1;
        voxelVol = vw * vh * vd;

        // ThresholdModel only used to drive HistogramPanel
        model = ThresholdModel.createFor3DPlugin(imp);

        // Set initial threshold to 20% of max
        threshold = model.getTBg();
        model.setTBg(threshold);

        int imgMin = model.getMinValue();
        int imgMax = model.getMaxValue();
        if (imgMax <= imgMin) imgMax = imgMin + 1;

        // Build UI components
        threshBar   = new Scrollbar(Scrollbar.HORIZONTAL, threshold, 1, imgMin, imgMax + 1);
        threshField = new TextField(Integer.toString(threshold), 6);

        minVolEnabled = true;
        maxVolEnabled = true;
        minVolVal     = 0.1;
        maxVolVal     = 50.0;

        minVolCheck = new Checkbox("", minVolEnabled);
        minVolBar   = makeVolBar(minVolVal);
        minVolField = new TextField(formatVol(minVolVal), 7);

        maxVolCheck = new Checkbox("", maxVolEnabled);
        maxVolBar   = makeVolBar(maxVolVal);
        maxVolField = new TextField(formatVol(maxVolVal), 7);

        gaussEnabled = false;
        gaussXYVal   = 1.0;
        gaussZVal    = 0.5;
        gaussCheck   = new Checkbox("Gaussian blur", gaussEnabled);
        gaussXYField = new TextField(Double.toString(gaussXYVal), 4);
        gaussZField  = new TextField(Double.toString(gaussZVal),  4);
        gaussXYField.setEnabled(false);
        gaussZField .setEnabled(false);

        connectivityChoice = new Choice();
        connectivityChoice.add("6");
        connectivityChoice.add("18");
        connectivityChoice.add("26");
        connectivityChoice.select("6");
        fillHolesCheck = new Checkbox("Fill holes", false);

        previewOff     = new Checkbox("Off",     previewGroup, true);
        previewOverlay = new Checkbox("Overlay", previewGroup, false);
        previewRoi     = new Checkbox("ROI",     previewGroup, false);

        histogramPanel = new HistogramPanel(imp, model, this::onHistogramThreshold);
        histogramPanel.setFgEnabled(false);

        buildLayout();
        wireEvents();
        startZWatch();
        pack();
        placeNearImage();
    }

    // =========================================================
    // Layout
    // =========================================================

    private void buildLayout() {
        setLayout(new BorderLayout(4, 4));

        // Histogram at top
        Panel top = new Panel(new BorderLayout());
        top.add(histogramPanel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // Parameter rows in center
        Panel center = new Panel(new GridLayout(0, 1, 2, 2));
        center.add(makeThreshRow());
        center.add(makeVolRow("Min vol µm³:", minVolCheck, minVolBar, minVolField));
        center.add(makeVolRow("Max vol µm³:", maxVolCheck, maxVolBar, maxVolField));
        center.add(makeGaussRow());
        center.add(makeConnectivityRow());
        center.add(makePreviewRow());
        add(center, BorderLayout.CENTER);

        // Buttons at bottom
        Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        buttons.add(applyBtn);
        buttons.add(addRoiBtn);
        buttons.add(saveRoiBtn);
        buttons.add(saveCsvBtn);
        buttons.add(saveAllBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private Panel makeThreshRow() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = baseGbc();
        c.gridx = 0; p.add(new Label("Threshold:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        threshBar.setPreferredSize(new Dimension(240, 18));
        p.add(threshBar, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(threshField, c);
        return p;
    }

    private Panel makeVolRow(String label, Checkbox check, Scrollbar bar, TextField field) {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = baseGbc();
        c.gridx = 0; p.add(check, c);
        c.gridx = 1; p.add(new Label(label), c);
        c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        bar.setPreferredSize(new Dimension(200, 18));
        p.add(bar, c);
        c.gridx = 3; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(field, c);
        return p;
    }

    private Panel makeGaussRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(gaussCheck);
        p.add(new Label("XY σ:"));
        p.add(gaussXYField);
        p.add(new Label("Z σ:"));
        p.add(gaussZField);
        return p;
    }

    private Panel makeConnectivityRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Connectivity:"));
        p.add(connectivityChoice);
        p.add(fillHolesCheck);
        return p;
    }

    private Panel makePreviewRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Preview:"));
        p.add(previewOff);
        p.add(previewOverlay);
        p.add(previewRoi);
        return p;
    }

    private static GridBagConstraints baseGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy  = 0;
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    // =========================================================
    // Events
    // =========================================================

    private void wireEvents() {
        // Threshold slider + field
        threshBar.addAdjustmentListener(e -> {
            if (syncing) return;
            threshold = threshBar.getValue();
            model.setTBg(threshold);
            syncing = true;
            threshField.setText(Integer.toString(threshold));
            histogramPanel.repaint();
            syncing = false;
            onThreshOrGaussChanged();
        });
        threshField.addActionListener(e -> commitThreshField());
        threshField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitThreshField(); }
        });

        // Min vol checkbox
        minVolCheck.addItemListener(e -> {
            if (syncing) return;
            minVolEnabled = minVolCheck.getState();
            minVolBar.setEnabled(minVolEnabled);
            minVolField.setEnabled(minVolEnabled);
            onSizeFilterChanged();
        });
        minVolBar.addAdjustmentListener(e -> {
            if (syncing) return;
            minVolVal = sliderToVol(minVolBar.getValue());
            syncing = true;
            minVolField.setText(formatVol(minVolVal));
            syncing = false;
            onSizeFilterChanged();
        });
        minVolField.addActionListener(e -> commitMinVolField());
        minVolField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitMinVolField(); }
        });

        // Max vol checkbox
        maxVolCheck.addItemListener(e -> {
            if (syncing) return;
            maxVolEnabled = maxVolCheck.getState();
            maxVolBar.setEnabled(maxVolEnabled);
            maxVolField.setEnabled(maxVolEnabled);
            onSizeFilterChanged();
        });
        maxVolBar.addAdjustmentListener(e -> {
            if (syncing) return;
            maxVolVal = sliderToVol(maxVolBar.getValue());
            syncing = true;
            maxVolField.setText(formatVol(maxVolVal));
            syncing = false;
            onSizeFilterChanged();
        });
        maxVolField.addActionListener(e -> commitMaxVolField());
        maxVolField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitMaxVolField(); }
        });

        // Gaussian blur
        gaussCheck.addItemListener(e -> {
            if (syncing) return;
            gaussEnabled = gaussCheck.getState();
            gaussXYField.setEnabled(gaussEnabled);
            gaussZField .setEnabled(gaussEnabled);
            onThreshOrGaussChanged();
        });
        gaussXYField.addActionListener(e -> commitGaussXYField());
        gaussXYField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitGaussXYField(); }
        });
        gaussZField.addActionListener(e -> commitGaussZField());
        gaussZField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitGaussZField(); }
        });

        // Connectivity / fill holes
        connectivityChoice.addItemListener(e -> onThreshOrGaussChanged());
        fillHolesCheck.addItemListener(e -> onThreshOrGaussChanged());

        // Preview radio
        ItemListener previewListener = e -> {
            if (syncing) return;
            onPreviewModeChanged();
        };
        previewOff    .addItemListener(previewListener);
        previewOverlay.addItemListener(previewListener);
        previewRoi    .addItemListener(previewListener);

        // Buttons
        applyBtn  .addActionListener(e -> runApply());
        addRoiBtn .addActionListener(e -> runAddRoi());
        saveRoiBtn.addActionListener(e -> runSaveRoi());
        saveCsvBtn.addActionListener(e -> runSaveCsvOnly());
        saveAllBtn.addActionListener(e -> runSaveAll());

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                clearOverlay();
                zWatchTimer.cancel();
                previewTimer.cancel();
                dispose();
            }
        });
    }

    // =========================================================
    // State changes
    // =========================================================

    /** Called when threshold or gauss settings change → invalidate CC cache, debounce preview. */
    private void onThreshOrGaussChanged() {
        cachedCc    = null;
        ccCacheKey  = null;
        schedulePreview();
    }

    /** Called when only the size filter changes → reuse cached CC, just redraw colors. */
    private void onSizeFilterChanged() {
        schedulePreview();
    }

    private void onPreviewModeChanged() {
        if (previewOff.getState()) {
            clearOverlay();
            cancelPreview();
        } else {
            schedulePreview();
        }
    }

    private void onHistogramThreshold(int tBg, int tFg) {
        if (syncing) return;
        threshold = tBg;
        model.setTBg(threshold);
        syncing = true;
        threshBar.setValue(threshold);
        threshField.setText(Integer.toString(threshold));
        syncing = false;
        onThreshOrGaussChanged();
    }

    // =========================================================
    // Commit helpers
    // =========================================================

    private void commitThreshField() {
        if (syncing) return;
        int v = parseIntOr(threshField.getText(), threshold);
        v = Math.max(0, v);
        threshold = v;
        model.setTBg(threshold);
        syncing = true;
        threshBar.setValue(threshold);
        threshField.setText(Integer.toString(threshold));
        histogramPanel.repaint();
        syncing = false;
        onThreshOrGaussChanged();
    }

    private void commitMinVolField() {
        if (syncing) return;
        double v = parseDoubleOr(minVolField.getText(), minVolVal);
        v = Math.max(0, v);
        minVolVal = v;
        syncing = true;
        minVolField.setText(formatVol(minVolVal));
        minVolBar.setValue(volToSlider(minVolVal));
        syncing = false;
        onSizeFilterChanged();
    }

    private void commitMaxVolField() {
        if (syncing) return;
        double v = parseDoubleOr(maxVolField.getText(), maxVolVal);
        v = Math.max(0, v);
        maxVolVal = v;
        syncing = true;
        maxVolField.setText(formatVol(maxVolVal));
        maxVolBar.setValue(volToSlider(maxVolVal));
        syncing = false;
        onSizeFilterChanged();
    }

    private void commitGaussXYField() {
        if (syncing) return;
        gaussXYVal = Math.max(0.1, parseDoubleOr(gaussXYField.getText(), gaussXYVal));
        syncing = true;
        gaussXYField.setText(Double.toString(gaussXYVal));
        syncing = false;
        if (gaussEnabled) onThreshOrGaussChanged();
    }

    private void commitGaussZField() {
        if (syncing) return;
        gaussZVal = Math.max(0.1, parseDoubleOr(gaussZField.getText(), gaussZVal));
        syncing = true;
        gaussZField.setText(Double.toString(gaussZVal));
        syncing = false;
        if (gaussEnabled) onThreshOrGaussChanged();
    }

    // =========================================================
    // Preview
    // =========================================================

    private void schedulePreview() {
        if (previewOff.getState()) return;
        boolean roiMode = previewRoi.getState();
        int gen = previewGen.incrementAndGet();
        cancelPreviewTask();
        int zPlane = imp.getCurrentSlice();
        QuantifierParams params = buildParams();
        previewTask = new TimerTask() {
            @Override public void run() {
                CcResult3D cc = getOrComputeCC(params);
                if (cc == null || previewGen.get() != gen) return;
                Map<Integer, Integer> status = cc.classifyLabels(params, voxelVol);
                EventQueue.invokeLater(() -> {
                    if (previewGen.get() != gen) return;
                    if (roiMode) {
                        renderRoiOverlay(cc.buildFilteredResult(status), zPlane);
                    } else {
                        renderOverlay(cc, status, zPlane);
                    }
                });
            }
        };
        previewTimer.schedule(previewTask, 200);
    }

    private void updatePreviewForZChange() {
        if (previewOff.getState() || cachedCc == null) return;
        int zPlane = imp.getCurrentSlice();
        QuantifierParams params = buildParams();
        Map<Integer, Integer> status = cachedCc.classifyLabels(params, voxelVol);
        if (previewRoi.getState()) {
            renderRoiOverlay(cachedCc.buildFilteredResult(status), zPlane);
        } else {
            renderOverlay(cachedCc, status, zPlane);
        }
    }

    private void cancelPreview() {
        previewGen.incrementAndGet();
        cancelPreviewTask();
    }

    private void cancelPreviewTask() {
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
    }

    /**
     * Render color-coded overlay on the current Z-plane:
     *   yellow = valid, red = too small, blue = too large.
     */
    private void renderOverlay(CcResult3D cc, Map<Integer, Integer> statusMap, int zPlane) {
        if (cc == null || cc.labelImage == null) return;
        if (zPlane < 1 || zPlane > cc.labelImage.getNSlices()) return;

        ImageProcessor labelIp = cc.labelImage.getStack().getProcessor(zPlane);
        int w = labelIp.getWidth();
        int h = labelIp.getHeight();

        ColorProcessor cp = new ColorProcessor(w, h);
        int[] pixels = (int[]) cp.getPixels();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int label = (int) Math.round(labelIp.getPixelValue(x, y));
                if (label <= 0) continue;
                Integer st = statusMap.get(label);
                if (st == null) continue;
                int color;
                switch (st) {
                    case CcResult3D.STATUS_TOO_SMALL: color = COLOR_TOO_SMALL; break;
                    case CcResult3D.STATUS_TOO_LARGE: color = COLOR_TOO_LARGE; break;
                    default:                          color = COLOR_VALID;      break;
                }
                pixels[y * w + x] = color;
            }
        }

        ImageRoi iroi = new ImageRoi(0, 0, cp);
        iroi.setZeroTransparent(true);
        iroi.setOpacity(1.0); // alpha already encoded in each pixel
        Overlay overlay = new Overlay();
        overlay.add(iroi);
        imp.setOverlay(overlay);
        imp.updateAndDraw();
    }

    /**
     * Render valid-spot ROI outlines for the current Z-plane as an overlay.
     * Shows exactly the ROIs that would be saved, without touching the ROI Manager.
     */
    private void renderRoiOverlay(SegmentationResult3D seg, int zPlane) {
        if (seg == null || seg.labelImage == null) return;
        if (zPlane < 1 || zPlane > seg.labelImage.getNSlices()) return;

        ImageProcessor labelIp = seg.labelImage.getStack().getProcessor(zPlane);
        int w = labelIp.getWidth(), h = labelIp.getHeight();

        TreeSet<Integer> labels = new TreeSet<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = (int) Math.round(labelIp.getPixelValue(x, y));
                if (v > 0) labels.add(v);
            }

        Overlay overlay = new Overlay();
        for (int label : labels) {
            ByteProcessor bp = new ByteProcessor(w, h);
            byte[] pixels = (byte[]) bp.getPixels();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((int) Math.round(labelIp.getPixelValue(x, y)) == label)
                        pixels[y * w + x] = (byte) 255;
            bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roi = ThresholdToSelection.run(new ImagePlus("", bp));
            if (roi == null) continue;
            roi.setStrokeColor(Color.YELLOW);
            roi.setStrokeWidth(1.0f);
            overlay.add(roi);
        }
        imp.setOverlay(overlay);
        imp.updateAndDraw();
    }

    private void clearOverlay() {
        imp.setOverlay((Overlay) null);
        imp.updateAndDraw();
    }

    // =========================================================
    // CC cache
    // =========================================================

    private synchronized CcResult3D getOrComputeCC(QuantifierParams params) {
        String key = ccKey(params);
        if (ccCacheKey != null && ccCacheKey.equals(key) && cachedCc != null) {
            return cachedCc;
        }
        try {
            cachedCc   = SpotQuantifier3D.computeCC(imp, params);
            ccCacheKey = key;
            // Update vol slider ranges from CC result
            if (!cachedCc.voxelCounts.isEmpty()) {
                EventQueue.invokeLater(this::updateVolSliderRanges);
            }
            return cachedCc;
        } catch (Exception ex) {
            IJ.log("Spot Quantifier 3D preview error: " + ex.getMessage());
            return null;
        }
    }

    private String ccKey(QuantifierParams p) {
        return p.threshold + ":" + p.gaussianBlur + ":" + p.gaussXY + ":" + p.gaussZ
             + ":" + p.connectivity + ":" + p.fillHoles;
    }

    private void updateVolSliderRanges() {
        if (cachedCc == null || cachedCc.voxelCounts.isEmpty()) return;
        volRangeMin = cachedCc.minVolUm3(voxelVol);
        volRangeMax = cachedCc.maxVolUm3(voxelVol);
        if (volRangeMin <= 0) volRangeMin = voxelVol;
        if (volRangeMax <= volRangeMin) volRangeMax = volRangeMin * 10;
        // Don't reset current values, just clamp sliders
        syncing = true;
        minVolBar.setValue(volToSlider(minVolVal));
        maxVolBar.setValue(volToSlider(maxVolVal));
        syncing = false;
    }

    // =========================================================
    // Button actions
    // =========================================================

    private void runApply() {
        QuantifierParams params = buildParams();
        CcResult3D cc = getOrComputeCC(params);
        if (cc == null) { IJ.error("Spot Quantifier 3D", "No spots detected."); return; }
        Map<Integer, Integer> status = cc.classifyLabels(params, voxelVol);
        SegmentationResult3D seg = cc.buildFilteredResult(status);
        seg.labelImage.show();
    }

    private void runAddRoi() {
        SegmentationResult3D seg = computeFilteredSeg();
        if (seg == null) return;
        new RoiExporter3D().exportToRoiManager(seg.labelImage);
    }

    private void runSaveRoi() {
        SegmentationResult3D seg = computeFilteredSeg();
        if (seg == null) return;
        RoiManager rm = RoiManager.getRoiManager();
        if (rm == null || rm.getCount() == 0) {
            new RoiExporter3D().exportToRoiManager(seg.labelImage);
        }
        FileDialog fd = new FileDialog(this, "Save ROIs as ZIP", FileDialog.SAVE);
        fd.setFile(imp.getShortTitle() + "_RoiSet.zip");
        fd.setVisible(true);
        String dir = fd.getDirectory();
        String name = fd.getFile();
        if (dir == null || name == null) return;
        RoiExporter.saveRoiManagerToZip(dir + name);
    }

    /** Save CSV only (no params, no ROI). */
    private void runSaveCsvOnly() {
        List<SpotMeasurement> spots = computeSpots();
        if (spots == null) return;

        FileDialog fd = new FileDialog(this, "Save CSV", FileDialog.SAVE);
        fd.setFile(imp.getShortTitle() + "_spots.csv");
        fd.setVisible(true);
        String dirStr = fd.getDirectory();
        String name   = fd.getFile();
        if (dirStr == null || name == null) return;

        File csvFile = new File(dirStr, name.endsWith(".csv") ? name : name + ".csv");
        try {
            CsvExporter.writeCsv(spots, csvFile);
            IJ.showMessage("Saved", spots.size() + " spot(s) → " + csvFile.getName());
        } catch (Exception ex) {
            IJ.error("Spot Quantifier 3D", "Save failed: " + ex.getMessage());
        }
    }

    /** Save CSV + params.txt + ROI ZIP (all 3). */
    private void runSaveAll() {
        QuantifierParams params = buildParams();
        CcResult3D cc = getOrComputeCC(params);
        if (cc == null || cc.voxelCounts.isEmpty()) {
            IJ.error("Spot Quantifier 3D", "No spots detected — adjust threshold.");
            return;
        }
        Map<Integer, Integer> status = cc.classifyLabels(params, voxelVol);
        SegmentationResult3D seg = cc.buildFilteredResult(status);
        List<SpotMeasurement> spots = SpotMeasurer.measure(seg, imp, vw, vh, vd);

        FileDialog fd = new FileDialog(this, "Choose output folder (select any file inside it)", FileDialog.SAVE);
        fd.setFile(imp.getShortTitle() + "_spots.csv");
        fd.setVisible(true);
        String dirStr = fd.getDirectory();
        String name   = fd.getFile();
        if (dirStr == null || name == null) return;

        File   outDir     = new File(dirStr);
        File   csvDir     = new File(outDir, "csv");
        File   roiDir     = new File(outDir, "roi");
        if (!csvDir.exists()) csvDir.mkdirs();
        if (!roiDir.exists()) roiDir.mkdirs();

        String basename   = name.replaceAll("_spots\\.csv$", "").replaceAll("\\.csv$", "");
        File   csvFile    = new File(csvDir,  basename + "_spots.csv");
        File   paramsFile = new File(outDir,  "params.txt");
        File   roiFile    = new File(roiDir,  basename + "_RoiSet.zip");
        try {
            CsvExporter.writeCsv(spots, csvFile);
            CsvExporter.writeParams(params, paramsFile);

            RoiManager rm = RoiManager.getRoiManager();
            rm.reset();
            new RoiExporter3D().exportToRoiManager(seg.labelImage);
            if (rm.getCount() > 0) {
                RoiExporter.saveRoiManagerToZip(roiFile.getAbsolutePath());
            }

            IJ.showMessage("Saved",
                spots.size() + " spot(s) saved.\n" +
                "csv/" + csvFile.getName() + "\n" +
                paramsFile.getName() + "\n" +
                "roi/" + roiFile.getName());
        } catch (Exception ex) {
            IJ.error("Spot Quantifier 3D", "Save failed: " + ex.getMessage());
        }
    }

    private List<SpotMeasurement> computeSpots() {
        QuantifierParams params = buildParams();
        CcResult3D cc = getOrComputeCC(params);
        if (cc == null || cc.voxelCounts.isEmpty()) {
            IJ.error("Spot Quantifier 3D", "No spots detected — adjust threshold.");
            return null;
        }
        Map<Integer, Integer> status = cc.classifyLabels(params, voxelVol);
        SegmentationResult3D seg = cc.buildFilteredResult(status);
        return SpotMeasurer.measure(seg, imp, vw, vh, vd);
    }

    private SegmentationResult3D computeFilteredSeg() {
        QuantifierParams params = buildParams();
        CcResult3D cc = getOrComputeCC(params);
        if (cc == null) { IJ.error("Spot Quantifier 3D", "No spots detected."); return null; }
        Map<Integer, Integer> status = cc.classifyLabels(params, voxelVol);
        return cc.buildFilteredResult(status);
    }

    // =========================================================
    // Z-watch
    // =========================================================

    private void startZWatch() {
        lastZ = imp.getCurrentSlice();
        zWatchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                int z = imp.getCurrentSlice();
                if (z != lastZ) {
                    lastZ = z;
                    EventQueue.invokeLater(() -> updatePreviewForZChange());
                }
            }
        }, 300, 300);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private QuantifierParams buildParams() {
        Double minVol = minVolEnabled ? minVolVal : null;
        Double maxVol = maxVolEnabled ? maxVolVal : null;
        int conn = Integer.parseInt(connectivityChoice.getSelectedItem());
        boolean fillH = fillHolesCheck.getState();
        return new QuantifierParams(threshold, minVol, maxVol, gaussEnabled, gaussXYVal, gaussZVal,
                                    conn, fillH);
    }

    /** Volume slider uses log scale over [volRangeMin, volRangeMax]. */
    private int volToSlider(double vol) {
        if (volRangeMax <= volRangeMin || volRangeMin <= 0) return 0;
        vol = Math.max(volRangeMin, Math.min(volRangeMax, vol));
        double ratio = Math.log(vol / volRangeMin) / Math.log(volRangeMax / volRangeMin);
        return (int) Math.round(ratio * VOL_SLIDER_STEPS);
    }

    private double sliderToVol(int pos) {
        if (volRangeMax <= volRangeMin || volRangeMin <= 0) return volRangeMin;
        double ratio = pos / (double) VOL_SLIDER_STEPS;
        return volRangeMin * Math.pow(volRangeMax / volRangeMin, ratio);
    }

    private Scrollbar makeVolBar(double initVal) {
        int initPos = volToSlider(initVal);
        return new Scrollbar(Scrollbar.HORIZONTAL, initPos, 1, 0, VOL_SLIDER_STEPS + 1);
    }

    private static String formatVol(double v) {
        if (v >= 10) return String.format("%.1f", v);
        if (v >= 1)  return String.format("%.2f", v);
        return String.format("%.4f", v);
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private static double parseDoubleOr(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private void placeNearImage() {
        Window active = imp.getWindow();
        if (active == null) return;
        Point p;
        try { p = active.getLocationOnScreen(); }
        catch (IllegalComponentStateException ex) { return; }
        int x = p.x + active.getWidth() + 8;
        int y = p.y;
        Rectangle screen = active.getGraphicsConfiguration() != null
            ? active.getGraphicsConfiguration().getBounds()
            : new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        if (x + getWidth() > screen.x + screen.width) x = screen.x + screen.width - getWidth();
        if (x < screen.x) x = screen.x;
        if (y + getHeight() > screen.y + screen.height) y = screen.y + screen.height - getHeight();
        if (y < screen.y) y = screen.y;
        setLocation(x, y);
    }
}
