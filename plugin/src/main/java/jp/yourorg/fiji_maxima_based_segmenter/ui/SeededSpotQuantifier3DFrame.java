package jp.yourorg.fiji_maxima_based_segmenter.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingWorker;

/**
 * GUI frame for Seeded_Spot_Quantifier_3D_.
 *
 * Layout:
 *   [Histogram (full 3D stack, two threshold lines)]
 *   Area threshold:  [slider] [field]    ← low threshold (spot extent / domain)
 *   Seed threshold:  [slider] [field]    ← high threshold (seed detection)
 *   [✓] Min vol µm³: [slider] [field]   ← seed size filter
 *   [✓] Max vol µm³: [slider] [field]
 *   [□] Gaussian blur  XY:[field] Z:[field]
 *   Connectivity: [6▼]  [□] Fill holes
 *   Preview: ○ Off  ● Overlay  ○ ROI
 *   [Apply]  ROI color: [▼]  [Add ROI]  [Save ROI]  [Save CSV]  [Save All]  [Batch…]
 */
public class SeededSpotQuantifier3DFrame extends PlugInFrame {

    // --- Preview overlay colors ---
    // (filled overlay colors are derived dynamically from seedColorChoice / roiColorChoice)

    /** Selectable colors for the ROI preview outline. */
    static final String[][] PREVIEW_COLOR_OPTIONS = {
        { "Yellow",  "#FFFF00" },
        { "Purple",  "#AA00FF" },
        { "Cyan",    "#00FFFF" },
        { "Magenta", "#FF00FF" },
        { "Red",     "#FF0000" },
        { "Green",   "#00FF00" },
        { "White",   "#FFFFFF" },
    };

    private final ImagePlus imp;
    private final ThresholdModel model;

    // Calibration (µm per pixel)
    private final double vw, vh, vd, voxelVol;

    // --- UI components ---
    private final HistogramPanel histogramPanel;

    private final Checkbox  areaEnabledCheck;
    private final Scrollbar areaThreshBar;
    private final TextField areaThreshField;

    private final Scrollbar seedThreshBar;
    private final TextField seedThreshField;

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

    private final Button applyBtn   = new Button("Apply");
    private final Button addRoiBtn  = new Button("Add ROI");
    private final Button saveRoiBtn = new Button("Save ROI");
    private final Button saveCsvBtn = new Button("Save CSV");
    private final Button saveAllBtn = new Button("Save All");
    private final Button batchBtn   = new Button("Batch…");

    private final Choice    seedColorChoice;
    private final Choice    roiColorChoice;
    private final TextField overlayOpacityField;

    private final Choice zprojChoice;
    private final Button zprojRefreshBtn = new Button("Reload");

    private final Label statusLabel = new Label("", Label.LEFT);

    // --- State ---
    private boolean syncing = false;

    private boolean areaEnabled;
    private int     areaThreshold;
    private int     seedThreshold;
    private boolean minVolEnabled, maxVolEnabled;
    private double  minVolVal, maxVolVal;
    private boolean gaussEnabled;
    private double  gaussXYVal, gaussZVal;

    // --- Segmentation cache ---
    private SeededQuantifier3D.SeededResult cachedSeededResult;
    private String                          segCacheKey;

    // --- Z-proj render cache (invalidated with segmentation cache) ---
    private int[]      cachedZProjTypeMap;   // 0=bg, 1=seed, 2=area  (Overlay mode)
    private List<Roi>  cachedZProjSeedRois;  // union outlines for seed labels (ROI mode)
    private List<Roi>  cachedZProjAreaRois;  // union outlines for area labels (ROI mode)

    // Vol slider ranges
    private double volRangeMin = 0.01;
    private double volRangeMax = 100.0;
    private static final int VOL_SLIDER_STEPS = 1000;

    // --- Preview / Z-watch timers ---
    private final Timer previewTimer = new Timer("ssq3d-preview", true);
    private TimerTask   previewTask;
    private final AtomicInteger previewGen = new AtomicInteger();
    private final Timer zWatchTimer = new Timer("ssq3d-zwatch", true);
    private int lastZ = -1;

    public SeededSpotQuantifier3DFrame(ImagePlus imp) {
        super("Seeded Spot Quantifier 3D");
        this.imp = imp;

        Calibration cal = imp.getCalibration();
        vw       = cal.pixelWidth  > 0 ? cal.pixelWidth  : 1;
        vh       = cal.pixelHeight > 0 ? cal.pixelHeight : 1;
        vd       = cal.pixelDepth  > 0 ? cal.pixelDepth  : 1;
        voxelVol = vw * vh * vd;

        model = ThresholdModel.createFor3DPlugin(imp);

        int imgMin = model.getMinValue();
        int imgMax = model.getMaxValue();
        if (imgMax <= imgMin) imgMax = imgMin + 1;

        // Initial thresholds: area = 20% of max, seed = 40% of max
        areaEnabled   = true;
        areaThreshold = model.getTBg();           // ThresholdModel default (20% of max)
        seedThreshold = Math.min(imgMax, areaThreshold * 2);
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);

        areaEnabledCheck = new Checkbox("", areaEnabled);
        areaThreshBar    = new Scrollbar(Scrollbar.HORIZONTAL, areaThreshold, 1, imgMin, imgMax + 1);
        areaThreshField  = new TextField(Integer.toString(areaThreshold), 6);

        seedThreshBar   = new Scrollbar(Scrollbar.HORIZONTAL, seedThreshold, 1, imgMin, imgMax + 1);
        seedThreshField = new TextField(Integer.toString(seedThreshold), 6);

        minVolEnabled = true;
        maxVolEnabled = false;
        minVolVal     = 0.1;
        maxVolVal     = 50.0;

        minVolCheck = new Checkbox("", minVolEnabled);
        minVolBar   = makeVolBar(minVolVal);
        minVolField = new TextField(formatVol(minVolVal), 7);

        maxVolCheck = new Checkbox("", maxVolEnabled);
        maxVolBar   = makeVolBar(maxVolVal);
        maxVolBar.setEnabled(maxVolEnabled);
        maxVolField = new TextField(formatVol(maxVolVal), 7);
        maxVolField.setEnabled(maxVolEnabled);

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

        seedColorChoice = new Choice();
        for (String[] entry : PREVIEW_COLOR_OPTIONS) seedColorChoice.add(entry[0]);
        seedColorChoice.select(1); // Purple

        roiColorChoice = new Choice();
        for (String[] entry : PREVIEW_COLOR_OPTIONS) roiColorChoice.add(entry[0]);
        roiColorChoice.select(0); // Yellow

        overlayOpacityField = new TextField("50", 3);

        zprojChoice = new Choice();
        refreshZProjChoiceItems();

        histogramPanel = new HistogramPanel(imp, model, this::onHistogramThresholds);
        histogramPanel.setFgEnabled(true); // show both area and seed threshold lines

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

        Panel top = new Panel(new BorderLayout());
        top.add(histogramPanel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        Panel center = new Panel(new GridLayout(0, 1, 2, 2));
        center.add(makeAreaThreshRow());
        center.add(makeThreshRow("Seed threshold:", seedThreshBar, seedThreshField));
        center.add(makeVolRow("Min vol µm³ (seed):", minVolCheck, minVolBar, minVolField));
        center.add(makeVolRow("Max vol µm³ (seed):", maxVolCheck, maxVolBar, maxVolField));
        center.add(makeGaussRow());
        center.add(makeConnectivityRow());
        center.add(makePreviewRow());
        center.add(makeColorsRow());
        center.add(makeZProjRow());
        center.add(makeStatusRow());
        add(center, BorderLayout.CENTER);

        Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        buttons.add(applyBtn);
        buttons.add(addRoiBtn);
        buttons.add(saveRoiBtn);
        buttons.add(saveCsvBtn);
        buttons.add(saveAllBtn);
        buttons.add(batchBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private Panel makeAreaThreshRow() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = baseGbc();
        c.gridx = 0; p.add(areaEnabledCheck, c);
        c.gridx = 1; p.add(new Label("Area threshold:"), c);
        c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        areaThreshBar.setPreferredSize(new Dimension(220, 18));
        p.add(areaThreshBar, c);
        c.gridx = 3; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(areaThreshField, c);
        return p;
    }

    private Panel makeThreshRow(String label, Scrollbar bar, TextField field) {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = baseGbc();
        c.gridx = 0; p.add(new Label(label), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        bar.setPreferredSize(new Dimension(240, 18));
        p.add(bar, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(field, c);
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

    private Panel makeZProjRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Z-proj:"));
        p.add(zprojChoice);
        p.add(zprojRefreshBtn);
        return p;
    }

    private Panel makeStatusRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 1));
        statusLabel.setForeground(new Color(80, 80, 80));
        p.add(statusLabel);
        return p;
    }

    private Panel makeColorsRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Colors:"));
        p.add(new Label("Seed:"));
        p.add(seedColorChoice);
        p.add(new Label("Area/ROI:"));
        p.add(roiColorChoice);
        p.add(new Label("Opacity %:"));
        p.add(overlayOpacityField);
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
        // Area enabled checkbox
        areaEnabledCheck.addItemListener(e -> {
            if (syncing) return;
            areaEnabled = areaEnabledCheck.getState();
            areaThreshBar.setEnabled(areaEnabled);
            areaThreshField.setEnabled(areaEnabled);
            onParamsChanged();
        });

        // Area threshold
        areaThreshBar.addAdjustmentListener(e -> {
            if (syncing) return;
            areaThreshold = areaThreshBar.getValue();
            model.setTBg(areaThreshold);
            syncing = true;
            areaThreshField.setText(Integer.toString(areaThreshold));
            histogramPanel.repaint();
            syncing = false;
            onParamsChanged();
        });
        areaThreshField.addActionListener(e -> commitAreaThreshField());
        areaThreshField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitAreaThreshField(); }
        });

        // Seed threshold
        seedThreshBar.addAdjustmentListener(e -> {
            if (syncing) return;
            seedThreshold = seedThreshBar.getValue();
            model.setTFg(seedThreshold);
            syncing = true;
            seedThreshField.setText(Integer.toString(seedThreshold));
            histogramPanel.repaint();
            syncing = false;
            onParamsChanged();
        });
        seedThreshField.addActionListener(e -> commitSeedThreshField());
        seedThreshField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitSeedThreshField(); }
        });

        // Min vol
        minVolCheck.addItemListener(e -> {
            if (syncing) return;
            minVolEnabled = minVolCheck.getState();
            minVolBar.setEnabled(minVolEnabled);
            minVolField.setEnabled(minVolEnabled);
            onParamsChanged();
        });
        minVolBar.addAdjustmentListener(e -> {
            if (syncing) return;
            minVolVal = sliderToVol(minVolBar.getValue());
            syncing = true;
            minVolField.setText(formatVol(minVolVal));
            syncing = false;
            onParamsChanged();
        });
        minVolField.addActionListener(e -> commitMinVolField());
        minVolField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitMinVolField(); }
        });

        // Max vol
        maxVolCheck.addItemListener(e -> {
            if (syncing) return;
            maxVolEnabled = maxVolCheck.getState();
            maxVolBar.setEnabled(maxVolEnabled);
            maxVolField.setEnabled(maxVolEnabled);
            onParamsChanged();
        });
        maxVolBar.addAdjustmentListener(e -> {
            if (syncing) return;
            maxVolVal = sliderToVol(maxVolBar.getValue());
            syncing = true;
            maxVolField.setText(formatVol(maxVolVal));
            syncing = false;
            onParamsChanged();
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
            onParamsChanged();
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
        connectivityChoice.addItemListener(e -> onParamsChanged());
        fillHolesCheck.addItemListener(e -> onParamsChanged());

        // Preview
        ItemListener previewListener = e -> onPreviewModeChanged();
        previewOff    .addItemListener(previewListener);
        previewOverlay.addItemListener(previewListener);
        previewRoi    .addItemListener(previewListener);

        // Color choices and opacity — re-render overlay only, no re-segmentation
        seedColorChoice.addItemListener(e -> onColorChanged());
        roiColorChoice .addItemListener(e -> onColorChanged());
        overlayOpacityField.addActionListener(e -> onColorChanged());
        overlayOpacityField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { onColorChanged(); }
        });

        // Z-proj window selection
        zprojChoice    .addItemListener(e -> onColorChanged());
        zprojRefreshBtn.addActionListener(e -> refreshZProjChoiceItems());

        // Buttons
        applyBtn  .addActionListener(e -> runApply());
        addRoiBtn .addActionListener(e -> runAddRoi());
        saveRoiBtn.addActionListener(e -> runSaveRoi());
        saveCsvBtn.addActionListener(e -> runSaveCsvOnly());
        saveAllBtn.addActionListener(e -> runSaveAll());
        batchBtn  .addActionListener(e -> runBatch());

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
    // Histogram callback
    // =========================================================

    private void onHistogramThresholds(int tBg, int tFg) {
        if (syncing) return;
        areaThreshold = tBg;
        seedThreshold = tFg;
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);
        syncing = true;
        areaThreshBar.setValue(areaThreshold);
        areaThreshField.setText(Integer.toString(areaThreshold));
        seedThreshBar.setValue(seedThreshold);
        seedThreshField.setText(Integer.toString(seedThreshold));
        syncing = false;
        onParamsChanged();
    }

    // =========================================================
    // State changes
    // =========================================================

    private void onParamsChanged() {
        cachedSeededResult  = null;
        segCacheKey         = null;
        cachedZProjTypeMap  = null;
        cachedZProjSeedRois = null;
        cachedZProjAreaRois = null;
        setModified();
    }

    private void onPreviewModeChanged() {
        if (previewOff.getState()) {
            clearOverlay();
            cancelPreview();
            statusLabel.setText("");
            IJ.showStatus("");
        } else {
            // Restore overlay from cache if available; otherwise prompt user to press Apply
            if (cachedSeededResult != null) {
                updatePreviewForZChange();
            } else {
                setModified();
            }
        }
    }

    /** Mark params as changed without running any computation. */
    private void setModified() {
        statusLabel.setText("Press Apply to update");
        IJ.showStatus("");
    }

    /** Color choice changed — re-render current overlay without re-segmenting. */
    private void onColorChanged() {
        if (previewOff.getState() || cachedSeededResult == null) return;
        int zPlane = imp.getCurrentSlice();
        if (previewRoi.getState())
            renderRoiOverlay(cachedSeededResult.seedSeg, cachedSeededResult.finalSeg,
                             areaEnabled, zPlane);
        else
            renderOverlay(cachedSeededResult.seedSeg, cachedSeededResult.finalSeg, areaEnabled, zPlane);
    }

    // =========================================================
    // Commit helpers
    // =========================================================

    private void commitAreaThreshField() {
        if (syncing) return;
        int v = parseIntOr(areaThreshField.getText(), areaThreshold);
        v = Math.max(0, v);
        areaThreshold = v;
        model.setTBg(areaThreshold);
        syncing = true;
        areaThreshBar.setValue(areaThreshold);
        areaThreshField.setText(Integer.toString(areaThreshold));
        histogramPanel.repaint();
        syncing = false;
        onParamsChanged();
    }

    private void commitSeedThreshField() {
        if (syncing) return;
        int v = parseIntOr(seedThreshField.getText(), seedThreshold);
        v = Math.max(0, v);
        seedThreshold = v;
        model.setTFg(seedThreshold);
        syncing = true;
        seedThreshBar.setValue(seedThreshold);
        seedThreshField.setText(Integer.toString(seedThreshold));
        histogramPanel.repaint();
        syncing = false;
        onParamsChanged();
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
        onParamsChanged();
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
        onParamsChanged();
    }

    private void commitGaussXYField() {
        if (syncing) return;
        gaussXYVal = Math.max(0.1, parseDoubleOr(gaussXYField.getText(), gaussXYVal));
        syncing = true;
        gaussXYField.setText(Double.toString(gaussXYVal));
        syncing = false;
        if (gaussEnabled) onParamsChanged();
    }

    private void commitGaussZField() {
        if (syncing) return;
        gaussZVal = Math.max(0.1, parseDoubleOr(gaussZField.getText(), gaussZVal));
        syncing = true;
        gaussZField.setText(Double.toString(gaussZVal));
        syncing = false;
        if (gaussEnabled) onParamsChanged();
    }

    // =========================================================
    // Preview
    // =========================================================

    // schedulePreview() removed — computation is now triggered only by runApply().

    private void updatePreviewForZChange() {
        if (previewOff.getState() || cachedSeededResult == null) return;
        int zPlane = imp.getCurrentSlice();
        if (previewRoi.getState())
            renderRoiOverlay(cachedSeededResult.seedSeg, cachedSeededResult.finalSeg,
                             areaEnabled, zPlane);
        else
            renderOverlay(cachedSeededResult.seedSeg, cachedSeededResult.finalSeg, areaEnabled, zPlane);
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
     * Overlay preview: seed pixels filled with seed color, area-only pixels filled with area color.
     * When areaEnabled=false, seedSeg==finalSeg so everything is colored with seed color.
     */
    private void renderOverlay(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                boolean areaEn, int zPlane) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int nSlices = finalSeg.labelImage.getNSlices();
        if (zPlane < 1 || zPlane > nSlices) return;

        ImageProcessor finalIp = finalSeg.labelImage.getStack().getProcessor(zPlane);
        int w = finalIp.getWidth(), h = finalIp.getHeight();

        ImageProcessor seedIp = (areaEn && seedSeg != null && seedSeg.labelImage != null
                                  && zPlane <= seedSeg.labelImage.getNSlices())
                                ? seedSeg.labelImage.getStack().getProcessor(zPlane)
                                : null;

        int seedRgb = toRgbSolid(selectedSeedPreviewColor());
        int areaRgb = toRgbSolid(selectedRoiColor());
        double opacity = getOverlayOpacity();

        ColorProcessor cp = new ColorProcessor(w, h);
        int[] pixels = (int[]) cp.getPixels();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean isSeed = seedIp != null
                    && (int) Math.round(seedIp.getPixelValue(x, y)) > 0;
                int finalLabel = (int) Math.round(finalIp.getPixelValue(x, y));
                if (isSeed) {
                    pixels[y * w + x] = seedRgb;
                } else if (finalLabel > 0) {
                    pixels[y * w + x] = areaRgb;
                }
            }
        }

        ImageRoi iroi = new ImageRoi(0, 0, cp);
        iroi.setZeroTransparent(true);
        iroi.setOpacity(opacity);
        Overlay overlay = new Overlay();
        overlay.add(iroi);
        imp.setOverlay(overlay);
        imp.updateAndDraw();

        ImagePlus zp = getZProjImp();
        if (zp != null) renderOverlayOnZProj(seedSeg, finalSeg, areaEn, zp);
    }

    /** Solid RGB pixel value (no alpha) for ImageRoi fill. */
    private static int toRgbSolid(Color c) {
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    /** Overlay opacity from the input field, clamped to [0, 100] and converted to 0.0–1.0. */
    private double getOverlayOpacity() {
        try {
            int v = Integer.parseInt(overlayOpacityField.getText().trim());
            return Math.max(0, Math.min(100, v)) / 100.0;
        } catch (NumberFormatException ex) {
            return 0.5;
        }
    }

    /**
     * ROI Preview overlay with dual-color coding:
     *   cyan   = seed segmentation (seedSeg labels)
     *   yellow = final segmentation (finalSeg labels)
     * When areaEnabled=false, seed==final so only yellow is drawn.
     */
    private void renderRoiOverlay(SegmentationResult3D seedSeg,
                                   SegmentationResult3D finalSeg,
                                   boolean areaEnabled, int zPlane) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int nSlices = finalSeg.labelImage.getNSlices();
        if (zPlane < 1 || zPlane > nSlices) return;

        Overlay overlay = new Overlay();

        // Draw seed outlines (only when area is enabled and seedSeg differs from finalSeg)
        if (areaEnabled && seedSeg != null && seedSeg.labelImage != null
                && zPlane <= seedSeg.labelImage.getNSlices()) {
            addLabelOutlines(seedSeg.labelImage.getStack().getProcessor(zPlane),
                             selectedSeedPreviewColor(), overlay);
        }

        // Draw area outlines
        addLabelOutlines(finalSeg.labelImage.getStack().getProcessor(zPlane),
                         selectedRoiColor(), overlay);

        imp.setOverlay(overlay);
        imp.updateAndDraw();

        ImagePlus zp = getZProjImp();
        if (zp != null) renderRoiOverlayOnZProj(seedSeg, finalSeg, areaEnabled, zp);
    }

    /**
     * Extract per-label outlines from a label image slice and add to the overlay.
     * Uses bounding-box cropping: O(W*H) single pass to collect bboxes, then
     * ThresholdToSelection runs only on each label's bbox crop (O(Ai) per label).
     */
    private static void addLabelOutlines(ImageProcessor labelIp, Color color, Overlay overlay) {
        int w = labelIp.getWidth(), h = labelIp.getHeight();

        // Phase 1: single pass — record bounding box per label
        // bbox[label] = {minX, minY, maxX, maxY}
        HashMap<Integer, int[]> bboxMap = new HashMap<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (int) Math.round(labelIp.getPixelValue(x, y));
                if (v <= 0) continue;
                int[] bb = bboxMap.get(v);
                if (bb == null) {
                    bboxMap.put(v, new int[]{x, y, x, y});
                } else {
                    if (x < bb[0]) bb[0] = x;
                    if (y < bb[1]) bb[1] = y;
                    if (x > bb[2]) bb[2] = x;
                    if (y > bb[3]) bb[3] = y;
                }
            }
        }

        // Phase 2: per label, fill only the bbox region (+1px padding for closed boundary)
        for (Map.Entry<Integer, int[]> entry : bboxMap.entrySet()) {
            int label = entry.getKey();
            int[] bb = entry.getValue();
            int x0 = Math.max(0, bb[0] - 1), y0 = Math.max(0, bb[1] - 1);
            int x1 = Math.min(w - 1, bb[2] + 1), y1 = Math.min(h - 1, bb[3] + 1);
            int bw = x1 - x0 + 1, bh = y1 - y0 + 1;

            ByteProcessor bp = new ByteProcessor(bw, bh);
            byte[] pixels = (byte[]) bp.getPixels();
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    if ((int) Math.round(labelIp.getPixelValue(x, y)) == label)
                        pixels[(y - y0) * bw + (x - x0)] = (byte) 255;

            bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roi = ThresholdToSelection.run(new ImagePlus("", bp));
            if (roi == null) continue;
            // Translate roi from bbox-local coords back to full-image coords
            roi.setLocation(roi.getXBase() + x0, roi.getYBase() + y0);
            roi.setStrokeColor(color);
            overlay.add(roi);
        }
    }

    // =========================================================
    // Z-projection overlay rendering
    // =========================================================

    /** Returns the selected Z-proj ImagePlus, or null if None / closed / size mismatch. */
    private ImagePlus getZProjImp() {
        String title = zprojChoice.getSelectedItem();
        if (title == null || "None".equals(title)) return null;
        ImagePlus zp = WindowManager.getImage(title);
        if (zp == null || zp.getProcessor() == null) return null;
        if (zp.getWidth() != imp.getWidth() || zp.getHeight() != imp.getHeight()) return null;
        return zp;
    }

    /** Rebuild the Z-proj choice list from currently open images (excluding imp). */
    private void refreshZProjChoiceItems() {
        String current = zprojChoice.getSelectedItem();
        zprojChoice.removeAll();
        zprojChoice.add("None");
        int[] ids = WindowManager.getIDList();
        if (ids != null) {
            for (int id : ids) {
                ImagePlus zp = WindowManager.getImage(id);
                if (zp == null || zp == imp) continue;
                if (zp.getNSlices() != 1) continue;
                if (zp.getWidth() != imp.getWidth() || zp.getHeight() != imp.getHeight()) continue;
                zprojChoice.add(zp.getTitle());
            }
        }
        if (current != null) zprojChoice.select(current);
    }

    /** Overlay mode on Z-proj: uses cached typeMap; rebuilds only when cache is null. */
    private void renderOverlayOnZProj(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                       boolean areaEn, ImagePlus zp) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int w = finalSeg.labelImage.getWidth();
        int h = finalSeg.labelImage.getHeight();

        // Build typeMap once per segmentation result
        if (cachedZProjTypeMap == null) {
            int d = finalSeg.labelImage.getNSlices();
            boolean hasSeed = areaEn && seedSeg != null && seedSeg.labelImage != null;
            cachedZProjTypeMap = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int type = 0;
                    for (int z = 1; z <= d; z++) {
                        if (hasSeed && z <= seedSeg.labelImage.getNSlices()
                                && (int) Math.round(seedSeg.labelImage.getStack()
                                        .getProcessor(z).getPixelValue(x, y)) > 0) {
                            type = 1; // seed — highest priority
                            break;
                        }
                        if ((int) Math.round(finalSeg.labelImage.getStack()
                                .getProcessor(z).getPixelValue(x, y)) > 0) {
                            type = 2; // area-only
                        }
                    }
                    cachedZProjTypeMap[y * w + x] = type;
                }
            }
        }

        // Apply current colors to typeMap (fast, no label image scan)
        int seedRgb = toRgbSolid(selectedSeedPreviewColor());
        int areaRgb = toRgbSolid(selectedRoiColor());
        ColorProcessor cp = new ColorProcessor(w, h);
        int[] pixels = (int[]) cp.getPixels();
        for (int i = 0; i < cachedZProjTypeMap.length; i++) {
            if      (cachedZProjTypeMap[i] == 1) pixels[i] = seedRgb;
            else if (cachedZProjTypeMap[i] == 2) pixels[i] = areaRgb;
        }

        ImageRoi iroi = new ImageRoi(0, 0, cp);
        iroi.setZeroTransparent(true);
        iroi.setOpacity(getOverlayOpacity());
        Overlay overlay = new Overlay();
        overlay.add(iroi);
        zp.setOverlay(overlay);
        zp.updateAndDraw();
    }

    /** ROI mode on Z-proj: uses cached ROI shapes; rebuilds only when cache is null. */
    private void renderRoiOverlayOnZProj(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                          boolean areaEn, ImagePlus zp) {
        if (finalSeg == null || finalSeg.labelImage == null) return;

        // Build ROI shape lists once per segmentation result
        if (cachedZProjAreaRois == null) {
            cachedZProjAreaRois = buildLabelUnionRois(finalSeg.labelImage);
            cachedZProjSeedRois = (areaEn && seedSeg != null && seedSeg.labelImage != null)
                                  ? buildLabelUnionRois(seedSeg.labelImage) : null;
        }

        // Apply current colors to cached ROI shapes and build overlay (fast)
        Overlay overlay = new Overlay();
        if (cachedZProjSeedRois != null) {
            Color sc = selectedSeedPreviewColor();
            for (Roi r : cachedZProjSeedRois) { r.setStrokeColor(sc); overlay.add(r); }
        }
        Color ac = selectedRoiColor();
        for (Roi r : cachedZProjAreaRois) { r.setStrokeColor(ac); overlay.add(r); }
        zp.setOverlay(overlay);
        zp.updateAndDraw();
    }

    /** Build union-projected ROI outlines for every label in labelImp. */
    private static List<Roi> buildLabelUnionRois(ImagePlus labelImp) {
        int w = labelImp.getWidth(), h = labelImp.getHeight(), d = labelImp.getNSlices();
        TreeSet<Integer> labels = new TreeSet<>();
        for (int z = 1; z <= d; z++) {
            ImageProcessor ip = labelImp.getStack().getProcessor(z);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int v = (int) Math.round(ip.getPixelValue(x, y));
                    if (v > 0) labels.add(v);
                }
        }
        List<Roi> rois = new java.util.ArrayList<>();
        for (int label : labels) {
            ByteProcessor bp = new ByteProcessor(w, h);
            byte[] bpix = (byte[]) bp.getPixels();
            for (int z = 1; z <= d; z++) {
                ImageProcessor ip = labelImp.getStack().getProcessor(z);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        if ((int) Math.round(ip.getPixelValue(x, y)) == label)
                            bpix[y * w + x] = (byte) 255;
            }
            bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roi = ThresholdToSelection.run(new ImagePlus("", bp));
            if (roi != null) rois.add(roi);
        }
        return rois;
    }

    /**
     * For each label in labelImp, compute the Z-union mask across all slices,
     * convert to a 2D ROI via ThresholdToSelection, and add to the overlay.
     */
    private static void addLabelUnionOutlines(ImagePlus labelImp, Color color, Overlay overlay) {
        int w = labelImp.getWidth(), h = labelImp.getHeight(), d = labelImp.getNSlices();

        // Collect all label IDs present in the stack
        TreeSet<Integer> labels = new TreeSet<>();
        for (int z = 1; z <= d; z++) {
            ImageProcessor ip = labelImp.getStack().getProcessor(z);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int v = (int) Math.round(ip.getPixelValue(x, y));
                    if (v > 0) labels.add(v);
                }
        }

        for (int label : labels) {
            ByteProcessor bp = new ByteProcessor(w, h);
            byte[] bpix = (byte[]) bp.getPixels();
            for (int z = 1; z <= d; z++) {
                ImageProcessor ip = labelImp.getStack().getProcessor(z);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        if ((int) Math.round(ip.getPixelValue(x, y)) == label)
                            bpix[y * w + x] = (byte) 255;
            }
            bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roi = ThresholdToSelection.run(new ImagePlus("", bp));
            if (roi == null) continue;
            roi.setStrokeColor(color);
            overlay.add(roi);
        }
    }

    private void clearOverlay() {
        imp.setOverlay((Overlay) null);
        imp.updateAndDraw();
        ImagePlus zp = getZProjImp();
        if (zp != null) { zp.setOverlay((Overlay) null); zp.updateAndDraw(); }
    }

    // =========================================================
    // Segmentation cache
    // =========================================================

    private synchronized SeededQuantifier3D.SeededResult getOrComputeSeeded(
            QuantifierParams params, int at, int st, boolean areaEn) {
        String key = segKey(params, at, st, areaEn);
        if (segCacheKey != null && segCacheKey.equals(key) && cachedSeededResult != null) {
            return cachedSeededResult;
        }
        try {
            cachedSeededResult = SeededQuantifier3D.compute(imp, at, st, params, voxelVol, areaEn);
            segCacheKey        = key;
            return cachedSeededResult;
        } catch (Exception ex) {
            IJ.log("Seeded Spot Quantifier 3D preview error: " + ex.getMessage());
            return null;
        }
    }

    private String segKey(QuantifierParams p, int at, int st, boolean areaEn) {
        return at + ":" + st + ":" + areaEn + ":" + p.gaussianBlur + ":" + p.gaussXY
             + ":" + p.gaussZ + ":" + p.connectivity + ":" + p.fillHoles
             + ":" + p.minVolUm3 + ":" + p.maxVolUm3;
    }

    // =========================================================
    // Button actions
    // =========================================================

    private void runApply() {
        if (previewOff.getState()) return;
        statusLabel.setText("Computing...");
        IJ.showStatus("Seeded Spot Quantifier 3D: computing...");
        boolean roiMode = previewRoi.getState();
        int gen = previewGen.incrementAndGet();
        cancelPreviewTask();
        int zPlane = imp.getCurrentSlice();
        QuantifierParams params = buildParams();
        int at = areaThreshold;
        int st = seedThreshold;
        boolean areaEn = areaEnabled;
        previewTask = new TimerTask() {
            @Override public void run() {
                SeededQuantifier3D.SeededResult r = getOrComputeSeeded(params, at, st, areaEn);
                if (r == null || previewGen.get() != gen) {
                    EventQueue.invokeLater(() -> statusLabel.setText("No spots found."));
                    return;
                }
                int nSpots = (int) Math.round(r.finalSeg.labelImage.getStatistics().max);
                String msg = nSpots + " spot" + (nSpots != 1 ? "s" : "");
                IJ.showStatus("Seeded Spot Quantifier 3D: " + msg);
                EventQueue.invokeLater(() -> {
                    if (previewGen.get() != gen) return;
                    if (roiMode) renderRoiOverlay(r.seedSeg, r.finalSeg, areaEn, zPlane);
                    else         renderOverlay(r.seedSeg, r.finalSeg, areaEn, zPlane);
                    statusLabel.setText(msg);
                });
            }
        };
        previewTimer.schedule(previewTask, 0);
    }

    private void runAddRoi() {
        SegmentationResult3D seg = computeSeg();
        if (seg == null) return;
        new RoiExporter3D().exportToRoiManager(seg.labelImage, selectedRoiColor());
    }

    private void runSaveRoi() {
        SegmentationResult3D seg = computeSeg();
        if (seg == null) return;
        RoiManager rm = RoiManager.getRoiManager();
        if (rm == null || rm.getCount() == 0) {
            new RoiExporter3D().exportToRoiManager(seg.labelImage, selectedRoiColor());
        }
        FileDialog fd = new FileDialog(this, "Save ROIs as ZIP", FileDialog.SAVE);
        fd.setFile(imp.getShortTitle() + "_RoiSet.zip");
        fd.setVisible(true);
        String dir  = fd.getDirectory();
        String name = fd.getFile();
        if (dir == null || name == null) return;
        RoiExporter.saveRoiManagerToZip(dir + name);
    }

    private void runSaveCsvOnly() {
        List<SpotMeasurement> spots = computeSpots();
        if (spots == null) return;
        FileDialog fd = new FileDialog(this, "Save CSV", FileDialog.SAVE);
        fd.setFile(imp.getShortTitle() + "_spots.csv");
        fd.setVisible(true);
        String dir  = fd.getDirectory();
        String name = fd.getFile();
        if (dir == null || name == null) return;
        try {
            CsvExporter.writeCsv(spots, new File(dir, name));
        } catch (Exception ex) {
            IJ.error("Seeded Spot Quantifier 3D", "CSV save failed: " + ex.getMessage());
        }
    }

    private void runSaveAll() {
        QuantifierParams params = buildParams();
        FileDialog fd = new FileDialog(this, "Choose output folder (select any file inside it)",
            FileDialog.SAVE);
        fd.setFile(imp.getShortTitle() + "_spots.csv");
        fd.setVisible(true);
        if (fd.getDirectory() == null || fd.getFile() == null) return;

        File outDir = new File(fd.getDirectory());
        String err = saveOneToDir(imp, areaThreshold, seedThreshold, areaEnabled, params,
            outDir, selectedRoiColor());
        if (err == null) {
            String basename = imp.getShortTitle().replaceAll("\\.tiff?$", "");
            IJ.showMessage("Saved",
                imp.getNSlices() + " slices processed.\n" +
                "csv/" + basename + "_spots.csv\n" +
                "roi/" + basename + "_RoiSet.zip\n" +
                "params.txt");
        } else {
            IJ.error("Seeded Spot Quantifier 3D", "Save failed: " + err);
        }
    }

    /** Batch: apply current params to all TIFFs in a folder, save to output folder. */
    private void runBatch() {
        QuantifierParams params = buildParams();
        int batchAreaThreshold = areaThreshold;
        int batchSeedThreshold = seedThreshold;
        boolean batchAreaEnabled = areaEnabled;
        java.awt.Color batchRoiColor = selectedRoiColor();

        String inDirStr = IJ.getDirectory("Select input folder");
        if (inDirStr == null) return;

        String outDirStr = IJ.getDirectory("Select output folder");
        if (outDirStr == null) return;

        File inDir  = new File(inDirStr);
        File outDir = new File(outDirStr);

        File[] files = inDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".tif") || lower.endsWith(".tiff");
        });
        if (files == null || files.length == 0) {
            IJ.showMessage("Batch", "No TIFF files found in:\n" + inDirStr);
            return;
        }
        Arrays.sort(files);
        batchBtn.setEnabled(false);

        new SwingWorker<int[], String>() {
            @Override
            protected int[] doInBackground() {
                int ok = 0, skipped = 0;
                for (int i = 0; i < files.length; i++) {
                    IJ.showProgress(i, files.length);
                    IJ.showStatus("Batch " + (i + 1) + "/" + files.length
                        + ": " + files[i].getName());

                    ImagePlus target = IJ.openImage(files[i].getAbsolutePath());
                    if (target == null) {
                        publish("Batch SKIP (cannot open): " + files[i].getName());
                        skipped++;
                        continue;
                    }
                    if (target.getNSlices() < 2) {
                        publish("Batch SKIP (not 3D): " + files[i].getName());
                        target.close();
                        skipped++;
                        continue;
                    }

                    String err = saveOneToDir(target, batchAreaThreshold, batchSeedThreshold,
                        batchAreaEnabled, params, outDir, batchRoiColor);
                    target.close();
                    if (err != null) {
                        publish("Batch SKIP (" + err + "): " + files[i].getName());
                        skipped++;
                    } else {
                        publish("Batch OK: " + files[i].getName());
                        ok++;
                    }
                }
                return new int[]{ok, skipped};
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) IJ.log(msg);
            }

            @Override
            protected void done() {
                IJ.showProgress(1.0);
                IJ.showStatus("");
                batchBtn.setEnabled(true);
                try {
                    int[] counts = get();
                    int ok = counts[0], skipped = counts[1];
                    IJ.showMessage("Batch done",
                        ok + " file(s) processed.\n" +
                        (skipped > 0 ? skipped + " skipped — see Log for details.\n" : "") +
                        "Output: " + outDir.getAbsolutePath());
                } catch (Exception ex) {
                    IJ.error("Batch", "Batch failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Process one image and save csv/roi/params under outDir.
     * Returns null on success, error message string on failure.
     */
    private static String saveOneToDir(ImagePlus target, int at, int st, boolean areaEn,
                                        QuantifierParams params, File outDir,
                                        java.awt.Color roiColor) {
        Calibration cal = target.getCalibration();
        double tw = cal.pixelWidth  > 0 ? cal.pixelWidth  : 1;
        double th = cal.pixelHeight > 0 ? cal.pixelHeight : 1;
        double td = cal.pixelDepth  > 0 ? cal.pixelDepth  : 1;
        double tVoxelVol = tw * th * td;

        SeededQuantifier3D.SeededResult r = SeededQuantifier3D.compute(
            target, at, st, params, tVoxelVol, areaEn);
        if (r == null) return "no spots detected";
        SegmentationResult3D seg = r.finalSeg;

        List<SpotMeasurement> spots = SpotMeasurer.measure(seg, target, tw, th, td);

        File csvDir = new File(outDir, "csv");
        File roiDir = new File(outDir, "roi");
        csvDir.mkdirs();
        roiDir.mkdirs();

        String basename   = target.getShortTitle().replaceAll("\\.tiff?$", "");
        File   csvFile    = new File(csvDir, basename + "_spots.csv");
        File   paramsFile = new File(outDir, "params.txt");
        File   roiFile    = new File(roiDir, basename + "_RoiSet.zip");
        try {
            CsvExporter.writeCsv(spots, csvFile);
            CsvExporter.writeSeededParams(at, st, params, paramsFile);
            RoiManager rm = RoiManager.getRoiManager();
            rm.reset();
            new RoiExporter3D().exportToRoiManager(seg.labelImage, roiColor);
            if (rm.getCount() > 0) {
                RoiExporter.saveRoiManagerToZip(roiFile.getAbsolutePath());
            }
            return null;
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private List<SpotMeasurement> computeSpots() {
        QuantifierParams params = buildParams();
        SeededQuantifier3D.SeededResult r = getOrComputeSeeded(params, areaThreshold,
                                                                seedThreshold, areaEnabled);
        if (r == null) {
            IJ.error("Seeded Spot Quantifier 3D", "No spots detected — adjust thresholds.");
            return null;
        }
        return SpotMeasurer.measure(r.finalSeg, imp, vw, vh, vd);
    }

    private SegmentationResult3D computeSeg() {
        QuantifierParams params = buildParams();
        SeededQuantifier3D.SeededResult r = getOrComputeSeeded(params, areaThreshold,
                                                                seedThreshold, areaEnabled);
        if (r == null) {
            IJ.error("Seeded Spot Quantifier 3D", "No spots detected.");
            return null;
        }
        return r.finalSeg;
    }

    private QuantifierParams buildParams() {
        Double minVol = minVolEnabled ? minVolVal : null;
        Double maxVol = maxVolEnabled ? maxVolVal : null;
        int conn = Integer.parseInt(connectivityChoice.getSelectedItem());
        boolean fillH = fillHolesCheck.getState();
        // threshold field: use areaThreshold as placeholder (SeededQuantifier3D ignores it)
        return new QuantifierParams(areaThreshold, minVol, maxVol,
            gaussEnabled, gaussXYVal, gaussZVal, conn, fillH);
    }

    private Color selectedSeedPreviewColor() {
        return Color.decode(PREVIEW_COLOR_OPTIONS[seedColorChoice.getSelectedIndex()][1]);
    }

    private Color selectedRoiColor() {
        return Color.decode(PREVIEW_COLOR_OPTIONS[roiColorChoice.getSelectedIndex()][1]);
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
