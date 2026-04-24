package jp.yourorg.fiji_maxima_based_segmenter.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.ZProjector;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.alg.*;
import jp.yourorg.fiji_maxima_based_segmenter.core.ThresholdModel;
import jp.yourorg.fiji_maxima_based_segmenter.util.SeededSpotQuantifier3DImageSupport;
import jp.yourorg.fiji_maxima_based_segmenter.util.SeededSpotQuantifier3DSaveSupport;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * GUI frame for Seeded_Spot_Quantifier_3D_.
 */
public class SeededSpotQuantifier3DFrame extends PlugInFrame {
    private static final String NONE_ITEM = "None";

    static final String[][] PREVIEW_COLOR_OPTIONS = {
        { "Yellow",  "#FFFF00" },
        { "Purple",  "#AA00FF" },
        { "Cyan",    "#00FFFF" },
        { "Magenta", "#FF00FF" },
        { "Red",     "#FF0000" },
        { "Green",   "#00FF00" },
        { "White",   "#FFFFFF" },
    };

    private ImagePlus imp;
    private ImagePlus rawImp;
    private final ThresholdModel model;

    private double vw, vh, vd, voxelVol;

    // --- UI components ---
    private final HistogramPanel histogramPanel;
    private final Choice processingImageChoice;
    private final Choice channelChoice;
    private final Button imageReloadBtn = new Button("Reload");

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

    private final Choice   connectivityChoice;
    private final Checkbox fillHolesCheck;

    private final CheckboxGroup previewGroup = new CheckboxGroup();
    private final Checkbox previewOff;
    private final Checkbox previewOverlay;
    private final Checkbox previewRoi;

    private final Button applyBtn   = new Button("Apply");
    private final Button saveAllBtn = new Button("Save");
    private final Button cancelBtn  = new Button("Cancel");
    private final Button batchBtn   = new Button("Batch\u2026");

    // --- Save section ---
    private final Checkbox  saveSeedRoiCheck   = new Checkbox("Seed ROI",   false);
    private final Checkbox  saveSizeRoiCheck   = new Checkbox("Size ROI",   false);
    private final Checkbox  saveAreaRoiCheck   = new Checkbox("Area ROI",   false);
    private final Checkbox  saveResultRoiCheck = new Checkbox("Result ROI", false);
    private final Checkbox  saveResultRoiByObjectCheck = new Checkbox("Result ROI by object", true);
    private final Checkbox  saveCsvCheck       = new Checkbox("CSV",        true);
    private final Checkbox  saveParamCheck     = new Checkbox("Param",      true);
    private final Checkbox  customFolderCheck  = new Checkbox("Custom folder name:", false);
    private final TextField folderNameField    = new TextField("{name} result", 22);
    private final Button    saveToExecBtn      = new Button("Save to...");
    private final Button    saveToggleBtn      = new Button("\u25bc Save options");
    private       boolean   saveSectionExpanded = true;
    private       Panel     saveOptionsPanel;
    private       Panel     saveChecksGrid;
    private       Panel     centerPanel;

    private final Choice    seedColorChoice;
    private final Choice    roiColorChoice;
    private final TextField overlayOpacityField;

    private final Choice zprojChoice;
    private final Button createZProjBtn = new Button("Max Proj");

    private final Label statusLabel = new Label("", Label.LEFT);
    private boolean cleanupDone = false;

    // --- State ---
    private boolean syncing = false;

    private boolean areaEnabled;
    private int     areaThreshold;
    private int     seedThreshold;
    private boolean minVolEnabled, maxVolEnabled;
    private double  minVolVal, maxVolVal;

    // --- Segmentation cache ---
    private SeededQuantifier3D.SeededResult cachedSeededResult;
    private String                          segCacheKey;

    // --- Z-proj render cache ---
    private int[]      cachedZProjTypeMap;
    private List<Roi>  cachedZProjSeedRois;
    private List<Roi>  cachedZProjAreaRois;

    private double volRangeMin = 0.01;
    private double volRangeMax = 100.0;
    private static final int VOL_SLIDER_STEPS = 1000;

    private final Timer previewTimer = new Timer("ssq3d-preview", true);
    private TimerTask   previewTask;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicInteger previewGen = new AtomicInteger();
    private final Timer zWatchTimer = new Timer("ssq3d-zwatch", true);
    private int lastZ = -1;
    private int selectedCh = 1;
    private int[] processingImageIds = new int[0];
    private boolean ownsProcessingImage = false;
    private boolean zProjSelectionManual = false;
    private boolean updatingZProjChoice = false;
    private boolean operationRunning = false;
    private SwingWorker<?, ?> activeWorker;

    public SeededSpotQuantifier3DFrame(ImagePlus imp) {
        super("Seeded Spot Quantifier 3D");
        this.rawImp = (imp != null && imp.getNSlices() >= 2) ? imp : null;
        this.selectedCh = (this.rawImp != null) ? initialChannelFor(this.rawImp) : 1;
        this.imp = (this.rawImp != null) ? extractProcessingImage(this.rawImp, selectedCh) : createPlaceholderImage();
        this.ownsProcessingImage = this.imp != this.rawImp;

        refreshCalibration();

        model = ThresholdModel.createFor3DPlugin(this.imp);

        int[] minMax = SeededSpotQuantifier3DImageSupport.computeStackMinMax(this.imp);
        int imgMin = minMax[0];
        int imgMax = safeImgMax(imgMin, minMax[1]);

        areaEnabled   = true;
        areaThreshold = model.getTBg();
        seedThreshold = Math.min(imgMax, areaThreshold * 2);
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);

        areaEnabledCheck = new Checkbox("", areaEnabled);
        areaThreshBar    = new Scrollbar(Scrollbar.HORIZONTAL, areaThreshold, 1, imgMin, imgMax + 1);
        areaThreshField  = new TextField(Integer.toString(areaThreshold), 7);

        seedThreshBar   = new Scrollbar(Scrollbar.HORIZONTAL, seedThreshold, 1, imgMin, imgMax + 1);
        seedThreshField = new TextField(Integer.toString(seedThreshold), 7);

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

        connectivityChoice = new Choice();
        connectivityChoice.add("6");
        connectivityChoice.add("18");
        connectivityChoice.add("26");
        connectivityChoice.select("6");
        fillHolesCheck = new Checkbox("Fill holes", false);

        processingImageChoice = new Choice();
        channelChoice = new Choice();

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
        refreshProcessingImageChoices();
        refreshChannelChoice();
        refreshZProjChoiceItems();

        histogramPanel = new HistogramPanel(this.imp, model, this::onHistogramThresholds);
        histogramPanel.setFgEnabled(true);

        buildLayout();
        wireEvents();
        startZWatch();
        updateTargetAvailability();
        validate();
        pack();
        placeNearImage();
    }

    // =========================================================
    // Layout
    // =========================================================

    private void buildLayout() {
        setLayout(new BorderLayout(4, 4));

        Panel top = new Panel(new BorderLayout(4, 4));
        top.add(makeImageSelectionRow(), BorderLayout.NORTH);
        top.add(histogramPanel, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        centerPanel = new Panel(new GridBagLayout());
        int row = 0;
        addCenterRow(makeThreshRow("Seed threshold:", seedThreshBar, seedThreshField), row++);
        addCenterRow(makeVolRow("Min vol \u00b5m\u00b3 (seed):", minVolCheck, minVolBar, minVolField), row++);
        addCenterRow(makeVolRow("Max vol \u00b5m\u00b3 (seed):", maxVolCheck, maxVolBar, maxVolField), row++);
        addCenterRow(makeAreaThreshRow(), row++);
        addCenterRow(makeConnectivityRow(), row++);
        addCenterRow(makePreviewRow(), row++);
        addCenterRow(makeColorsRow(), row++);
        addCenterRow(makeSaveToggleRow(), row++);
        saveOptionsPanel = makeSaveOptionsPanel();
        addCenterRow(saveOptionsPanel, row++);
        addCenterRow(makeStatusRow(), row);
        add(centerPanel, BorderLayout.CENTER);

        Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        buttons.add(applyBtn);
        buttons.add(saveAllBtn);
        buttons.add(saveToExecBtn);
        buttons.add(cancelBtn);
        buttons.add(batchBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private Panel makeImageSelectionRow() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(new Label("Target:"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(processingImageChoice, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(imageReloadBtn, c);

        c.gridy = 1;
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(new Label("Ch:"), c);
        c.gridx = 1; c.weightx = 0.35; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(channelChoice, c);

        c.gridy = 2;
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(new Label("Z-proj:"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(zprojChoice, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(createZProjBtn, c);
        return p;
    }

    private void addCenterRow(Component comp, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(1, 0, 1, 0);
        centerPanel.add(comp, c);
    }

    /** Two-row row: label on top, slider+field on bottom. */
    private Panel makeThreshRow(String label, Scrollbar bar, TextField field) {
        Panel outer = new Panel(new GridLayout(2, 1, 1, 1));
        Panel labelRow = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        labelRow.add(new Label(label));
        outer.add(labelRow);
        outer.add(makeSliderFieldRow(bar, field));
        return outer;
    }

    private Panel makeAreaThreshRow() {
        Panel outer = new Panel(new GridLayout(2, 1, 1, 1));
        Panel labelRow = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        labelRow.add(areaEnabledCheck);
        labelRow.add(new Label("Area threshold:"));
        outer.add(labelRow);
        outer.add(makeSliderFieldRow(areaThreshBar, areaThreshField));
        return outer;
    }

    private Panel makeVolRow(String label, Checkbox check, Scrollbar bar, TextField field) {
        Panel outer = new Panel(new GridLayout(2, 1, 1, 1));
        Panel labelRow = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        labelRow.add(check);
        labelRow.add(new Label(label));
        outer.add(labelRow);
        outer.add(makeSliderFieldRow(bar, field));
        return outer;
    }

    /** Bottom row of a two-row slider block: slider stretches, field fixed. */
    private Panel makeSliderFieldRow(Scrollbar bar, TextField field) {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 4, 0, 2);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(bar, c);
        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 2, 0, 4);
        p.add(field, c);
        return p;
    }

    private Panel makeConnectivityRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Connectivity:"));
        p.add(connectivityChoice);
        p.add(fillHolesCheck);
        return p;
    }

    /** Z-proj row: label fixed, Choice stretches with window, Reload fixed. */
    private Panel makeZProjRow() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(new Label("Z-proj:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(zprojChoice, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        p.add(createZProjBtn, c);
        return p;
    }

    private Panel makeStatusRow() {
        Panel p = new Panel(new BorderLayout());
        statusLabel.setForeground(new Color(80, 80, 80));
        Font baseFont = statusLabel.getFont();
        if (baseFont == null) baseFont = p.getFont();
        if (baseFont == null) baseFont = new Font("Dialog", Font.PLAIN, 12);
        statusLabel.setFont(baseFont);
        FontMetrics fm = statusLabel.getFontMetrics(baseFont);
        int prefW = fm.stringWidth("Saving: writing result ROI...") + 16;
        int prefH = fm.getHeight() + 4;
        statusLabel.setPreferredSize(new Dimension(prefW, prefH));
        p.add(statusLabel, BorderLayout.CENTER);
        return p;
    }

    private Panel makeColorsRow() {
        Panel outer = new Panel(new GridLayout(2, 1, 1, 1));
        Panel row1 = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new Label("Colors:"));
        row1.add(new Label("Seed:"));
        row1.add(seedColorChoice);
        row1.add(new Label("Area/ROI:"));
        row1.add(roiColorChoice);
        Panel row2 = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new Label("Opacity %:"));
        row2.add(overlayOpacityField);
        outer.add(row1);
        outer.add(row2);
        return outer;
    }

    private Panel makePreviewRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Preview:"));
        p.add(previewOff);
        p.add(previewOverlay);
        p.add(previewRoi);
        return p;
    }

    private Panel makeSaveToggleRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(saveToggleBtn);
        return p;
    }

    private Panel makeSaveOptionsPanel() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 0, 1, 0);
        int row = 0;

        Panel selectRow = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        Button selectAllBtn   = new Button("Select All");
        Button deselectAllBtn = new Button("Deselect All");
        selectAllBtn  .addActionListener(e -> setSaveChecks(true));
        deselectAllBtn.addActionListener(e -> setSaveChecks(false));
        selectRow.add(selectAllBtn);
        selectRow.add(deselectAllBtn);
        c.gridy = row++;
        p.add(selectRow, c);

        saveChecksGrid = new Panel();
        rebuildSaveChecksGrid();
        c.gridy = row++;
        p.add(saveChecksGrid, c);

        Panel folderRow = new Panel(new GridBagLayout());
        GridBagConstraints fc = new GridBagConstraints();
        fc.gridy = 0;
        fc.insets = new Insets(0, 0, 0, 4);
        fc.anchor = GridBagConstraints.WEST;
        fc.gridx = 0;
        fc.weightx = 0;
        fc.fill = GridBagConstraints.NONE;
        folderRow.add(customFolderCheck, fc);
        fc.gridx = 1;
        fc.weightx = 1.0;
        fc.fill = GridBagConstraints.HORIZONTAL;
        folderRow.add(folderNameField, fc);
        c.gridy = row++;
        p.add(folderRow, c);

        Panel tokensRow = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tokensRow.add(new Label("tokens: {name} {date} {seed} {area}"));
        c.gridy = row;
        p.add(tokensRow, c);

        return p;
    }

    private void setSaveChecks(boolean state) {
        saveSeedRoiCheck  .setState(state);
        saveSizeRoiCheck  .setState(state);
        saveAreaRoiCheck  .setState(state);
        saveResultRoiCheck.setState(state);
        saveResultRoiByObjectCheck.setState(state);
        saveCsvCheck      .setState(state);
        saveParamCheck    .setState(state);
    }

    private void refreshCalibration() {
        if (imp == null) {
            vw = vh = vd = voxelVol = 1.0;
            return;
        }
        Calibration cal = imp.getCalibration();
        vw       = cal.pixelWidth  > 0 ? cal.pixelWidth  : 1;
        vh       = cal.pixelHeight > 0 ? cal.pixelHeight : 1;
        vd       = cal.pixelDepth  > 0 ? cal.pixelDepth  : 1;
        voxelVol = vw * vh * vd;
    }

    private static int safeImgMax(int imgMin, int imgMax) {
        return (imgMax <= imgMin) ? imgMin + 1 : imgMax;
    }

    private static int initialChannelFor(ImagePlus image) {
        int nCh = Math.max(1, image.getNChannels());
        int cur = image.getC();
        return Math.max(1, Math.min(nCh, cur > 0 ? cur : 1));
    }

    private static ImagePlus extractProcessingImage(ImagePlus image, int channel) {
        int nCh = Math.max(1, image.getNChannels());
        if (nCh <= 1) return image;
        int ch = Math.max(1, Math.min(nCh, channel));
        return new Duplicator().run(image, ch, ch, 1, image.getNSlices(), 1, image.getNFrames());
    }

    private void disposeProcessingImage(ImagePlus image, boolean owned) {
        if (image == null || !owned) return;
        image.flush();
    }

    private void refreshProcessingImageChoices() {
        ImagePlus currentRaw = rawImp;
        processingImageChoice.removeAll();
        processingImageChoice.add(NONE_ITEM);
        int[] ids = WindowManager.getIDList();
        List<Integer> validIds = new ArrayList<>();
        if (ids != null) {
            for (int id : ids) {
                ImagePlus img = WindowManager.getImage(id);
                if (img == null || img.getNSlices() < 2) continue;
                validIds.add(id);
                processingImageChoice.add(img.getTitle());
            }
        }
        processingImageIds = new int[validIds.size()];
        for (int i = 0; i < validIds.size(); i++) processingImageIds[i] = validIds.get(i);

        int selectIdx = 0;
        for (int i = 0; i < processingImageIds.length; i++) {
            ImagePlus img = WindowManager.getImage(processingImageIds[i]);
            if (img == currentRaw) { selectIdx = i + 1; break; }
        }
        if (processingImageChoice.getItemCount() > 0) processingImageChoice.select(selectIdx);
    }

    private void refreshChannelChoice() {
        int nCh = (rawImp != null) ? Math.max(1, rawImp.getNChannels()) : 1;
        channelChoice.removeAll();
        for (int ch = 1; ch <= nCh; ch++) channelChoice.add(Integer.toString(ch));
        int chIdx = Math.max(0, Math.min(nCh - 1, selectedCh - 1));
        channelChoice.select(chIdx);
        channelChoice.setEnabled(rawImp != null && nCh > 1);
    }

    private void changeTarget(ImagePlus newRawImp, int newChannel) {
        clearOverlay();
        cancelPreview();

        ImagePlus oldRawImp = rawImp;
        ImagePlus oldImp = imp;
        boolean oldOwned = ownsProcessingImage;
        rawImp = (newRawImp != null && newRawImp.getNSlices() >= 2) ? newRawImp : null;
        selectedCh = (rawImp != null) ? Math.max(1, Math.min(Math.max(1, rawImp.getNChannels()), newChannel)) : 1;
        imp = (rawImp != null) ? extractProcessingImage(rawImp, selectedCh) : createPlaceholderImage();
        ownsProcessingImage = imp != rawImp;
        if (imp == null && rawImp != null) imp = rawImp;
        if (oldImp != imp) disposeProcessingImage(oldImp, oldOwned);
        if (oldRawImp != rawImp) zProjSelectionManual = false;

        refreshCalibration();
        model.setImage(imp);

        int[] minMax = SeededSpotQuantifier3DImageSupport.computeStackMinMax(imp);
        int imgMin = minMax[0];
        int imgMax = safeImgMax(imgMin, minMax[1]);

        syncing = true;
        updateThreshSliderRanges(imgMin, imgMax);
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);
        areaThreshField.setText(Integer.toString(areaThreshold));
        seedThreshField.setText(Integer.toString(seedThreshold));
        refreshProcessingImageChoices();
        refreshChannelChoice();
        refreshZProjChoiceItems();
        histogramPanel.setImage(imp);
        syncing = false;

        onParamsChanged();
        updateTargetAvailability();
        setStatusText(rawImp != null ? "Press Apply to update" : "Select a 3D image.");
        validate();
    }

    private void updateThreshSliderRanges(int imgMin, int imgMax) {
        int max = safeImgMax(imgMin, imgMax);
        areaThreshBar.setValues(areaThreshold, 1, imgMin, max + 1);
        seedThreshBar.setValues(seedThreshold, 1, imgMin, max + 1);
    }

    private ImagePlus selectedRawImage() {
        int idx = processingImageChoice.getSelectedIndex();
        if (idx <= 0) return null;
        int arrIdx = idx - 1;
        if (arrIdx >= processingImageIds.length) return rawImp;
        ImagePlus img = WindowManager.getImage(processingImageIds[arrIdx]);
        return (img != null) ? img : rawImp;
    }

    private int currentZPlane() {
        if (rawImp == null) return 1;
        int z = rawImp.getZ();
        return z > 0 ? z : rawImp.getCurrentSlice();
    }

    private static ImagePlus createPlaceholderImage() {
        return IJ.createImage("ssq3d-none", "16-bit black", 1, 1, 2);
    }

    private void updateTargetAvailability() {
        boolean hasTarget = rawImp != null;
        histogramPanel.setEnabled(hasTarget);
        areaEnabledCheck.setEnabled(hasTarget);
        areaThreshBar.setEnabled(hasTarget && areaEnabled);
        areaThreshField.setEnabled(hasTarget && areaEnabled);
        seedThreshBar.setEnabled(hasTarget);
        seedThreshField.setEnabled(hasTarget);
        minVolCheck.setEnabled(hasTarget);
        minVolBar.setEnabled(hasTarget && minVolEnabled);
        minVolField.setEnabled(hasTarget && minVolEnabled);
        maxVolCheck.setEnabled(hasTarget);
        maxVolBar.setEnabled(hasTarget && maxVolEnabled);
        maxVolField.setEnabled(hasTarget && maxVolEnabled);
        connectivityChoice.setEnabled(hasTarget);
        fillHolesCheck.setEnabled(hasTarget);
        previewOff.setEnabled(hasTarget);
        previewOverlay.setEnabled(hasTarget);
        previewRoi.setEnabled(hasTarget);
        seedColorChoice.setEnabled(hasTarget);
        roiColorChoice.setEnabled(hasTarget);
        overlayOpacityField.setEnabled(hasTarget);
        zprojChoice.setEnabled(hasTarget);
        createZProjBtn.setEnabled(hasTarget);
        saveToggleBtn.setEnabled(hasTarget);
        saveOptionsPanel.setEnabled(hasTarget);
        saveSeedRoiCheck.setEnabled(hasTarget);
        saveSizeRoiCheck.setEnabled(hasTarget);
        saveAreaRoiCheck.setEnabled(hasTarget);
        saveResultRoiCheck.setEnabled(hasTarget);
        saveResultRoiByObjectCheck.setEnabled(hasTarget);
        saveCsvCheck.setEnabled(hasTarget);
        saveParamCheck.setEnabled(hasTarget);
        customFolderCheck.setEnabled(hasTarget);
        folderNameField.setEnabled(hasTarget && customFolderCheck.getState());
        saveToExecBtn.setEnabled(hasTarget && !operationRunning);
        applyBtn.setEnabled(hasTarget && !operationRunning);
        saveAllBtn.setEnabled(hasTarget && !operationRunning);
        batchBtn.setEnabled(hasTarget && !operationRunning);
        cancelBtn.setEnabled(operationRunning);
    }

    // =========================================================
    // Events
    // =========================================================

    private void wireEvents() {
        processingImageChoice.addItemListener(e -> {
            if (syncing || e.getStateChange() != ItemEvent.SELECTED) return;
            changeTarget(selectedRawImage(), selectedCh);
        });
        channelChoice.addItemListener(e -> {
            if (syncing || e.getStateChange() != ItemEvent.SELECTED) return;
            changeTarget(rawImp, channelChoice.getSelectedIndex() + 1);
        });
        imageReloadBtn.addActionListener(e -> {
            syncing = true;
            refreshProcessingImageChoices();
            refreshChannelChoice();
            refreshZProjChoiceItems();
            syncing = false;
            ImagePlus selected = selectedRawImage();
            if (selected != null) changeTarget(selected, selectedCh);
        });

        areaEnabledCheck.addItemListener(e -> {
            if (syncing) return;
            areaEnabled = areaEnabledCheck.getState();
            areaThreshBar.setEnabled(areaEnabled);
            areaThreshField.setEnabled(areaEnabled);
            onParamsChanged();
        });

        areaThreshBar.addAdjustmentListener(e -> {
            if (syncing) return;
            int prevBg = areaThreshold;
            areaThreshold = areaThreshBar.getValue();
            model.setTBg(areaThreshold);
            syncing = true;
            areaThreshBar.setValue(areaThreshold);
            areaThreshField.setText(Integer.toString(areaThreshold));
            histogramPanel.repaintThresholdMarkers(prevBg, model.getTFg());
            syncing = false;
            onParamsChanged();
        });
        areaThreshField.addActionListener(e -> commitAreaThreshField());
        areaThreshField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitAreaThreshField(); }
        });

        seedThreshBar.addAdjustmentListener(e -> {
            if (syncing) return;
            int prevFg = seedThreshold;
            seedThreshold = seedThreshBar.getValue();
            model.setTFg(seedThreshold);
            syncing = true;
            seedThreshBar.setValue(seedThreshold);
            seedThreshField.setText(Integer.toString(seedThreshold));
            histogramPanel.repaintThresholdMarkers(model.getTBg(), prevFg);
            syncing = false;
            onParamsChanged();
        });
        seedThreshField.addActionListener(e -> commitSeedThreshField());
        seedThreshField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitSeedThreshField(); }
        });

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

        connectivityChoice.addItemListener(e -> onParamsChanged());
        fillHolesCheck    .addItemListener(e -> onParamsChanged());

        ItemListener previewListener = e -> onPreviewModeChanged();
        previewOff    .addItemListener(previewListener);
        previewOverlay.addItemListener(previewListener);
        previewRoi    .addItemListener(previewListener);

        seedColorChoice    .addItemListener(e -> onColorChanged());
        roiColorChoice     .addItemListener(e -> onColorChanged());
        overlayOpacityField.addActionListener(e -> onColorChanged());
        overlayOpacityField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { onColorChanged(); }
        });

        zprojChoice.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) return;
            if (!updatingZProjChoice && !syncing) zProjSelectionManual = true;
            onColorChanged();
        });
        createZProjBtn.addActionListener(e -> createMaxProjection());

        saveToggleBtn.addActionListener(e -> toggleSaveSection());
        customFolderCheck.addItemListener(e -> updateTargetAvailability());
        saveToExecBtn.addActionListener(e -> runSaveTo());
        cancelBtn.addActionListener(e -> cancelCurrentOperation());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                updateSaveChecksColumns();
            }
        });

        applyBtn  .addActionListener(e -> runApply());
        saveAllBtn.addActionListener(e -> runSaveAll());
        batchBtn  .addActionListener(e -> runBatch());

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    private void toggleSaveSection() {
        int currentWidth = getWidth();
        saveSectionExpanded = !saveSectionExpanded;
        saveOptionsPanel.setVisible(saveSectionExpanded);
        saveToggleBtn.setLabel(saveSectionExpanded ? "\u25bc Save options" : "\u25b6 Save options");
        updateSaveChecksColumns();
        centerPanel.validate();
        pack();
        setSize(currentWidth, getHeight());
    }

    private File chooseSaveDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select save folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private void runSaveTo() {
        File dir = chooseSaveDirectory();
        if (dir != null) runSaveAll(dir);
    }

    @Override
    public void dispose() {
        cleanupResources();
        super.dispose();
    }

    private void cleanupResources() {
        if (cleanupDone) return;
        cleanupDone = true;
        clearOverlay();
        cancelPreviewTask();
        previewGen.incrementAndGet();
        zWatchTimer.cancel();
        previewTimer.cancel();
        cachedSeededResult = null;
        segCacheKey = null;
        cachedZProjTypeMap = null;
        cachedZProjSeedRois = null;
        cachedZProjAreaRois = null;
        processingImageIds = new int[0];
        ImagePlus currentImp = imp;
        boolean owned = ownsProcessingImage;
        imp = null;
        rawImp = null;
        ownsProcessingImage = false;
        disposeProcessingImage(currentImp, owned);
    }

    // =========================================================
    // Histogram callback
    // =========================================================

    private void onHistogramThresholds(int tBg, int tFg) {
        if (syncing) return;
        int prevBg = areaThreshold;
        int prevFg = seedThreshold;
        areaThreshold = tBg;
        seedThreshold = tFg;
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);
        syncing = true;
        areaThreshBar.setValue(areaThreshold);
        areaThreshField.setText(Integer.toString(areaThreshold));
        seedThreshBar.setValue(seedThreshold);
        seedThreshField.setText(Integer.toString(seedThreshold));
        histogramPanel.repaintThresholdMarkers(prevBg, prevFg);
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
            setStatusText("");
        } else {
            if (cachedSeededResult != null) {
                updatePreviewForZChange();
            } else {
                setModified();
            }
        }
    }

    private void setModified() {
        setStatusText("Press Apply to update");
    }

    private void onColorChanged() {
        if (previewOff.getState() || cachedSeededResult == null) return;
        int zPlane = currentZPlane();
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
        int prevBg = areaThreshold;
        int v = parseIntOr(areaThreshField.getText(), areaThreshold);
        v = Math.max(0, v);
        areaThreshold = v;
        model.setTBg(areaThreshold);
        syncing = true;
        areaThreshBar.setValue(areaThreshold);
        areaThreshField.setText(Integer.toString(areaThreshold));
        histogramPanel.repaintThresholdMarkers(prevBg, model.getTFg());
        syncing = false;
        onParamsChanged();
    }

    private void commitSeedThreshField() {
        if (syncing) return;
        int prevFg = seedThreshold;
        int v = parseIntOr(seedThreshField.getText(), seedThreshold);
        v = Math.max(0, v);
        seedThreshold = v;
        model.setTFg(seedThreshold);
        syncing = true;
        seedThreshBar.setValue(seedThreshold);
        seedThreshField.setText(Integer.toString(seedThreshold));
        histogramPanel.repaintThresholdMarkers(model.getTBg(), prevFg);
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

    // =========================================================
    // Preview
    // =========================================================

    private void updatePreviewForZChange() {
        if (previewOff.getState() || cachedSeededResult == null) return;
        int zPlane = currentZPlane();
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

    private void renderOverlay(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                boolean areaEn, int zPlane) {
        renderOverlay(seedSeg, finalSeg, areaEn, zPlane, null);
    }

    private void renderOverlay(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                boolean areaEn, int zPlane, Consumer<String> progress) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int nSlices = finalSeg.labelImage.getNSlices();
        if (zPlane < 1 || zPlane > nSlices) return;
        reportRenderProgress(progress, "rendering current Z overlay");

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
        if (rawImp != null) {
            rawImp.setOverlay(overlay);
            rawImp.updateAndDraw();
        }

        ImagePlus zp = getZProjImp();
        if (zp != null) {
            reportRenderProgress(progress, "building Z-proj overlay");
            renderOverlayOnZProj(seedSeg, finalSeg, areaEn, zp);
        }
    }

    private static int toRgbSolid(Color c) {
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private double getOverlayOpacity() {
        try {
            int v = Integer.parseInt(overlayOpacityField.getText().trim());
            return Math.max(0, Math.min(100, v)) / 100.0;
        } catch (NumberFormatException ex) {
            return 0.5;
        }
    }

    private void renderRoiOverlay(SegmentationResult3D seedSeg,
                                   SegmentationResult3D finalSeg,
                                   boolean areaEnabled, int zPlane) {
        renderRoiOverlay(seedSeg, finalSeg, areaEnabled, zPlane, null);
    }

    private void renderRoiOverlay(SegmentationResult3D seedSeg,
                                   SegmentationResult3D finalSeg,
                                   boolean areaEnabled, int zPlane,
                                   Consumer<String> progress) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int nSlices = finalSeg.labelImage.getNSlices();
        if (zPlane < 1 || zPlane > nSlices) return;
        reportRenderProgress(progress, "rendering current Z ROI overlay");

        Overlay overlay = new Overlay();

        if (areaEnabled && seedSeg != null && seedSeg.labelImage != null
                && zPlane <= seedSeg.labelImage.getNSlices()) {
            addLabelOutlines(seedSeg.labelImage.getStack().getProcessor(zPlane),
                             selectedSeedPreviewColor(), overlay);
        }

        addLabelOutlines(finalSeg.labelImage.getStack().getProcessor(zPlane),
                         selectedRoiColor(), overlay);

        if (rawImp != null) {
            rawImp.setOverlay(overlay);
            rawImp.updateAndDraw();
        }

        ImagePlus zp = getZProjImp();
        if (zp != null) {
            reportRenderProgress(progress, "building Z-proj ROI overlay");
            renderRoiOverlayOnZProj(seedSeg, finalSeg, areaEnabled, zp, progress);
        }
    }

    private static void addLabelOutlines(ImageProcessor labelIp, Color color, Overlay overlay) {
        int w = labelIp.getWidth(), h = labelIp.getHeight();

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
            roi.setLocation(roi.getXBase() + x0, roi.getYBase() + y0);
            roi.setStrokeColor(color);
            overlay.add(roi);
        }
    }

    // =========================================================
    // Z-projection overlay rendering
    // =========================================================

    private ImagePlus getZProjImp() {
        if (rawImp == null) return null;
        String title = zprojChoice.getSelectedItem();
        if (title == null || NONE_ITEM.equals(title)) return null;
        ImagePlus zp = WindowManager.getImage(title);
        if (zp == null || zp.getProcessor() == null) return null;
        if (zp.getWidth() != imp.getWidth() || zp.getHeight() != imp.getHeight()) return null;
        return zp;
    }

    private void createMaxProjection() {
        if (rawImp == null) return;
        ImagePlus result = createMaxProjection(rawImp);
        if (result == null) return;
        result.setTitle("MAX_" + rawImp.getTitle());
        result.show();
        refreshZProjChoiceItems();
    }

    private static ImagePlus createMaxProjection(ImagePlus source) {
        if (source == null) return null;
        int nCh = Math.max(1, source.getNChannels());
        int nT = Math.max(1, source.getNFrames());

        ZProjector proj = new ZProjector(source);
        proj.setMethod(ZProjector.MAX_METHOD);
        if (nCh > 1 || nT > 1) proj.doHyperStackProjection(true);
        else proj.doProjection();
        ImagePlus result = proj.getProjection();
        if (result == null) return null;
        if (nCh > 1 || nT > 1) {
            result.setOpenAsHyperStack(true);
        }
        if (source.getCalibration() != null) {
            result.setCalibration(source.getCalibration().copy());
        }
        return result;
    }

    private void refreshZProjChoiceItems() {
        updatingZProjChoice = true;
        try {
        if (rawImp == null) {
            zprojChoice.removeAll();
            zprojChoice.add(NONE_ITEM);
            zprojChoice.select(0);
            return;
        }
        String current = zprojChoice.getSelectedItem();
        zprojChoice.removeAll();
        zprojChoice.add(NONE_ITEM);
        String autoMatch = null;
        boolean autoMatchAmbiguous = false;
        int[] ids = WindowManager.getIDList();
        if (ids != null) {
            for (int id : ids) {
                ImagePlus zp = WindowManager.getImage(id);
                if (zp == null || zp == imp) continue;
                if (zp.getNSlices() != 1) continue;
                if (zp.getWidth() != imp.getWidth() || zp.getHeight() != imp.getHeight()) continue;
                zprojChoice.add(zp.getTitle());
                if (zp.getTitle().contains(rawImp.getTitle())) {
                    if (autoMatch == null) autoMatch = zp.getTitle();
                    else autoMatchAmbiguous = true;
                }
            }
        }
        if (zProjSelectionManual && current != null) {
            try {
                zprojChoice.select(current);
                if (current.equals(zprojChoice.getSelectedItem())) return;
            } catch (IllegalArgumentException ignored) {
                // Current choice disappeared; fall through to auto-selection.
            }
        }
        if (autoMatch != null && !autoMatchAmbiguous) zprojChoice.select(autoMatch);
        else zprojChoice.select(0);
        } finally {
            updatingZProjChoice = false;
        }
    }

    private void renderOverlayOnZProj(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                       boolean areaEn, ImagePlus zp) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int w = finalSeg.labelImage.getWidth();
        int h = finalSeg.labelImage.getHeight();

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
                            type = 1;
                            break;
                        }
                        if ((int) Math.round(finalSeg.labelImage.getStack()
                                .getProcessor(z).getPixelValue(x, y)) > 0) {
                            type = 2;
                        }
                    }
                    cachedZProjTypeMap[y * w + x] = type;
                }
            }
        }

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

    private void renderRoiOverlayOnZProj(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                          boolean areaEn, ImagePlus zp) {
        renderRoiOverlayOnZProj(seedSeg, finalSeg, areaEn, zp, null);
    }

    private void renderRoiOverlayOnZProj(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                          boolean areaEn, ImagePlus zp, Consumer<String> progress) {
        if (finalSeg == null || finalSeg.labelImage == null) return;

        if (cachedZProjAreaRois == null) {
            cachedZProjAreaRois = SeededSpotQuantifier3DImageSupport.buildLabelUnionRois(finalSeg.labelImage, "result", progress);
            cachedZProjSeedRois = (areaEn && seedSeg != null && seedSeg.labelImage != null)
                                  ? SeededSpotQuantifier3DImageSupport.buildLabelUnionRois(seedSeg.labelImage, "seed", progress) : null;
        }

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

    private void clearOverlay() {
        if (rawImp != null) {
            rawImp.setOverlay((Overlay) null);
            rawImp.updateAndDraw();
        }
        ImagePlus zp = getZProjImp();
        if (zp != null) { zp.setOverlay((Overlay) null); zp.updateAndDraw(); }
    }

    // =========================================================
    // Segmentation cache
    // =========================================================

    private synchronized SeededQuantifier3D.SeededResult getOrComputeSeeded(
            QuantifierParams params, int at, int st, boolean areaEn, Consumer<String> progress) {
        String key = segKey(params, at, st, areaEn);
        if (segCacheKey != null && segCacheKey.equals(key) && cachedSeededResult != null) {
            return cachedSeededResult;
        }
        cachedSeededResult = SeededQuantifier3D.compute(
            imp, at, st, params, voxelVol, areaEn, progress, () -> cancelRequested.get());
        segCacheKey        = key;
        return cachedSeededResult;
    }

    private String segKey(QuantifierParams p, int at, int st, boolean areaEn) {
        return at + ":" + st + ":" + areaEn + ":" + p.connectivity + ":" + p.fillHoles
             + ":" + p.minVolUm3 + ":" + p.maxVolUm3;
    }

    // =========================================================
    // Button actions
    // =========================================================

    private void runApply() {
        if (rawImp == null) {
            setStatusText("Select a 3D image.");
            return;
        }
        if (previewOff.getState()) return;
        beginOperation();
        setStatusText("Computing...");
        boolean roiMode = previewRoi.getState();
        int gen = previewGen.incrementAndGet();
        cancelPreviewTask();
        int zPlane = currentZPlane();
        QuantifierParams params = buildParams();
        int at = areaThreshold;
        int st = seedThreshold;
        boolean areaEn = areaEnabled;
        previewTask = new TimerTask() {
            @Override public void run() {
                try {
                    SeededQuantifier3D.SeededResult r = getOrComputeSeeded(params, at, st, areaEn,
                        stage -> EventQueue.invokeLater(() -> {
                            if (previewGen.get() != gen) return;
                            setStatusText("Applying: " + stage + "...");
                        }));
                    if (r == null || previewGen.get() != gen) {
                        EventQueue.invokeLater(() -> {
                            setStatusText(cancelRequested.get() ? "Cancelled." : "No spots found.");
                            endOperation();
                        });
                        return;
                    }
                    int nSpots = countLabels(r.finalSeg);
                    String msg = nSpots + " spot" + (nSpots != 1 ? "s" : "");
                    IJ.showStatus("Seeded Spot Quantifier 3D: " + msg);
                    EventQueue.invokeLater(() -> {
                        if (previewGen.get() != gen) return;
                        if (roiMode) renderRoiOverlay(r.seedSeg, r.finalSeg, areaEn, zPlane,
                            stage -> setStatusText("Applying: " + stage + "..."));
                        else renderOverlay(r.seedSeg, r.finalSeg, areaEn, zPlane,
                            stage -> setStatusText("Applying: " + stage + "..."));
                        setStatusText(msg);
                        endOperation();
                    });
                } catch (CancellationException ex) {
                    EventQueue.invokeLater(() -> {
                        setStatusText("Cancelled.");
                        endOperation();
                    });
                } catch (Exception ex) {
                    EventQueue.invokeLater(() -> {
                        setStatusText("Apply failed: " + ex.getMessage());
                        endOperation();
                    });
                }
            }
        };
        previewTimer.schedule(previewTask, 0);
    }

    // =========================================================
    // Save
    // =========================================================

    private void runSaveAll() {
        runSaveAll(null);
    }

    private void runSaveAll(File explicitDir) {
        if (rawImp == null) {
            setStatusText("Select a 3D image.");
            return;
        }
        File outDir = SeededSpotQuantifier3DSaveSupport.resolveOutputDir(
            this, rawImp, customFolderCheck.getState(), folderNameField.getText(),
            seedThreshold, areaThreshold, explicitDir);
        if (outDir == null) return;

        if (outDir.exists()) {
            int choice = JOptionPane.showOptionDialog(
                this,
                "Folder already exists:\n" + outDir.getAbsolutePath() + "\nOverwrite?",
                "Folder Exists",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"Overwrite", "Cancel"},
                "Cancel");
            if (choice != 0) return;
        }

        QuantifierParams params = buildParams();
        boolean saveSeedRoi = saveSeedRoiCheck.getState();
        boolean saveSizeRoi = saveSizeRoiCheck.getState();
        boolean saveAreaRoi = saveAreaRoiCheck.getState();
        boolean saveResultRoi = saveResultRoiCheck.getState();
        boolean saveResultRoiByObject = saveResultRoiByObjectCheck.getState();
        boolean saveCsv = saveCsvCheck.getState();
        boolean saveParam = saveParamCheck.getState();
        Color roiColor = selectedRoiColor();

        beginOperation();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setStatusText("Saving: preparing...");

        activeWorker = new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() {
                return SeededSpotQuantifier3DSaveSupport.saveOneToDir(
                    imp, rawImp != null ? rawImp : imp, selectedCh,
                    areaThreshold, seedThreshold, areaEnabled, params, outDir,
                    saveSeedRoi, saveSizeRoi, saveAreaRoi, saveResultRoi, saveResultRoiByObject,
                    saveCsv, saveParam, roiColor,
                    msg -> publish(msg), () -> isCancelled() || cancelRequested.get());
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) setStatusText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String err = get();
                    if (SeededSpotQuantifier3DSaveSupport.CANCELLED.equals(err)) {
                        setStatusText("Cancelled.");
                    } else if (err == null) {
                        setStatusText("Saved to " + outDir.getName());
                        IJ.showMessage("Saved", "Saved to:\n" + outDir.getAbsolutePath());
                    } else {
                        setStatusText("Save failed: " + err);
                        IJ.error("Seeded Spot Quantifier 3D", "Save failed: " + err);
                    }
                } catch (CancellationException ex) {
                    setStatusText("Cancelled.");
                } catch (Exception ex) {
                    setStatusText("Save failed: " + ex.getMessage());
                    IJ.error("Seeded Spot Quantifier 3D", "Save failed: " + ex.getMessage());
                } finally {
                    activeWorker = null;
                    endOperation();
                }
            }
        };
        activeWorker.execute();
    }

    private void runBatch() {
        new BatchDialog().setVisible(true);
    }

    private void beginOperation() {
        cancelRequested.set(false);
        operationRunning = true;
        updateTargetAvailability();
    }

    private void endOperation() {
        operationRunning = false;
        cancelRequested.set(false);
        updateTargetAvailability();
    }

    private void cancelCurrentOperation() {
        if (!operationRunning) return;
        cancelRequested.set(true);
        setStatusText("Cancelling...");
        previewGen.incrementAndGet();
        cancelPreviewTask();
        if (activeWorker != null) activeWorker.cancel(true);
    }

    private static void reportRenderProgress(Consumer<String> progress, String message) {
        if (progress != null) progress.accept(message);
    }

    private void rebuildSaveChecksGrid() {
        if (saveChecksGrid == null) return;
        int cols = currentSaveChecksColumns();
        saveChecksGrid.removeAll();
        saveChecksGrid.setLayout(new GridLayout(0, cols, 8, 2));
        saveChecksGrid.add(saveSeedRoiCheck);
        saveChecksGrid.add(saveSizeRoiCheck);
        saveChecksGrid.add(saveAreaRoiCheck);
        saveChecksGrid.add(saveResultRoiCheck);
        saveChecksGrid.add(saveResultRoiByObjectCheck);
        saveChecksGrid.add(saveCsvCheck);
        saveChecksGrid.add(saveParamCheck);
    }

    private void updateSaveChecksColumns() {
        if (saveChecksGrid == null) return;
        int cols = currentSaveChecksColumns();
        GridLayout layout = (GridLayout) saveChecksGrid.getLayout();
        if (layout.getColumns() == cols) return;
        rebuildSaveChecksGrid();
        saveChecksGrid.validate();
        saveChecksGrid.repaint();
        if (saveOptionsPanel != null) {
            saveOptionsPanel.validate();
            saveOptionsPanel.repaint();
        }
        int currentWidth = getWidth();
        pack();
        setSize(currentWidth, getHeight());
    }

    private int currentSaveChecksColumns() {
        int width = saveOptionsPanel != null ? saveOptionsPanel.getWidth() : 0;
        if (width <= 0) width = getWidth();
        if (width >= 520) return 3;
        if (width >= 340) return 2;
        return 1;
    }

    // =========================================================
    // Batch Dialog
    // =========================================================

    private class BatchDialog extends JDialog {

        private final JCheckBox bSaveSeedRoi;
        private final JCheckBox bSaveSizeRoi;
        private final JCheckBox bSaveAreaRoi;
        private final JCheckBox bSaveResultRoi;
        private final JCheckBox bSaveResultRoiByObject;
        private final JCheckBox bSaveCsv;
        private final JCheckBox bSaveParam;
        private final JCheckBox bCustomFolder;
        private final JTextField bFolderName;

        private final JTextField pathField  = new JTextField(40);
        private final JPanel     fileListPanel = new JPanel();
        private final List<JCheckBox> fileChecks = new ArrayList<>();
        private final JLabel     countLabel    = new JLabel("0 file(s) found");
        private final JProgressBar progressBar = new JProgressBar();
        private final JLabel     dlgStatus    = new JLabel(" ");
        private       JButton    runBtn;

        BatchDialog() {
            super(SeededSpotQuantifier3DFrame.this, "Batch Processing", false);

            bSaveSeedRoi   = new JCheckBox("Seed ROI",   saveSeedRoiCheck  .getState());
            bSaveSizeRoi   = new JCheckBox("Size ROI",   saveSizeRoiCheck  .getState());
            bSaveAreaRoi   = new JCheckBox("Area ROI",   saveAreaRoiCheck  .getState());
            bSaveResultRoi = new JCheckBox("Result ROI", saveResultRoiCheck.getState());
            bSaveResultRoiByObject = new JCheckBox("Result ROI by object", saveResultRoiByObjectCheck.getState());
            bSaveCsv       = new JCheckBox("CSV",        saveCsvCheck      .getState());
            bSaveParam     = new JCheckBox("Param",      saveParamCheck    .getState());
            bCustomFolder  = new JCheckBox("Custom folder name:", customFolderCheck.getState());
            bFolderName    = new JTextField(folderNameField.getText(), 22);

            buildBatchLayout();
        }

        private void buildBatchLayout() {
            setLayout(new BorderLayout(6, 6));
            JPanel main = new JPanel(new BorderLayout(4, 4));
            main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            // Input folder row
            JPanel inputRow = new JPanel(new BorderLayout(4, 0));
            inputRow.add(new JLabel("Input folder:"), BorderLayout.WEST);
            inputRow.add(pathField, BorderLayout.CENTER);
            JButton browseBtn = new JButton("Browse");
            browseBtn.addActionListener(e -> browsePath());
            inputRow.add(browseBtn, BorderLayout.EAST);
            main.add(inputRow, BorderLayout.NORTH);

            // File list
            fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
            JScrollPane scroll = new JScrollPane(fileListPanel);
            scroll.setPreferredSize(new Dimension(520, 180));
            main.add(scroll, BorderLayout.CENTER);

            // Bottom section
            JPanel bottom = new JPanel(new BorderLayout(4, 4));

            JPanel listCtrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            JButton selAll   = new JButton("Select All");
            JButton deselAll = new JButton("Deselect All");
            selAll  .addActionListener(e -> setAllFileChecks(true));
            deselAll.addActionListener(e -> setAllFileChecks(false));
            listCtrl.add(selAll);
            listCtrl.add(deselAll);
            listCtrl.add(countLabel);
            bottom.add(listCtrl, BorderLayout.NORTH);

            bottom.add(buildBatchSavePanel(), BorderLayout.CENTER);

            // Progress + buttons
            JPanel ctrlRow = new JPanel(new BorderLayout(4, 4));
            progressBar.setStringPainted(true);
            progressBar.setVisible(false);
            ctrlRow.add(progressBar, BorderLayout.CENTER);

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> dispose());
            runBtn = new JButton("Run");
            runBtn.addActionListener(e -> runBatchProcess());
            btnRow.add(dlgStatus);
            btnRow.add(cancelBtn);
            btnRow.add(runBtn);
            ctrlRow.add(btnRow, BorderLayout.SOUTH);
            bottom.add(ctrlRow, BorderLayout.SOUTH);

            main.add(bottom, BorderLayout.SOUTH);
            add(main);

            pathField.addActionListener(e -> scanFiles());
            pathField.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent e) { scanFiles(); }
            });

            pack();
            setLocationRelativeTo(SeededSpotQuantifier3DFrame.this);
        }

        private JPanel buildBatchSavePanel() {
            JPanel p = new JPanel(new GridLayout(0, 1, 2, 2));
            p.setBorder(BorderFactory.createTitledBorder("Save options"));

            JPanel selRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            JButton selAll   = new JButton("Select All");
            JButton deselAll = new JButton("Deselect All");
            selAll  .addActionListener(e -> setBatchSaveChecks(true));
            deselAll.addActionListener(e -> setBatchSaveChecks(false));
            selRow.add(selAll);
            selRow.add(deselAll);
            p.add(selRow);

            JPanel checksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            checksRow.add(bSaveSeedRoi);
            checksRow.add(bSaveSizeRoi);
            checksRow.add(bSaveAreaRoi);
            checksRow.add(bSaveResultRoi);
            checksRow.add(bSaveResultRoiByObject);
            checksRow.add(bSaveCsv);
            checksRow.add(bSaveParam);
            p.add(checksRow);

            JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            folderRow.add(bCustomFolder);
            folderRow.add(bFolderName);
            folderRow.add(new JLabel("tokens: {name} {date} {seed} {area}"));
            p.add(folderRow);

            return p;
        }

        private void setBatchSaveChecks(boolean state) {
            bSaveSeedRoi  .setSelected(state);
            bSaveSizeRoi  .setSelected(state);
            bSaveAreaRoi  .setSelected(state);
            bSaveResultRoi.setSelected(state);
            bSaveResultRoiByObject.setSelected(state);
            bSaveCsv      .setSelected(state);
            bSaveParam    .setSelected(state);
        }

        private void setAllFileChecks(boolean state) {
            for (JCheckBox cb : fileChecks) cb.setSelected(state);
        }

        private void browsePath() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select input folder");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            String current = pathField.getText().trim();
            if (!current.isEmpty()) {
                File currentDir = new File(current);
                chooser.setCurrentDirectory(currentDir.isDirectory() ? currentDir : currentDir.getParentFile());
            }

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                if (selectedDir != null) {
                    pathField.setText(selectedDir.getAbsolutePath());
                    scanFiles();
                }
            }
        }

        private void scanFiles() {
            String pathStr = pathField.getText().trim();
            if (pathStr.isEmpty()) return;
            File dir = new File(pathStr);
            if (!dir.isDirectory()) return;

            fileChecks.clear();
            fileListPanel.removeAll();

            List<File> tiffs = new ArrayList<>();
            try {
                Files.walk(dir.toPath())
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".tif") || n.endsWith(".tiff");
                    })
                    .sorted()
                    .forEach(p -> tiffs.add(p.toFile()));
            } catch (Exception ex) {
                IJ.log("Batch scan error: " + ex.getMessage());
            }

            for (File f : tiffs) {
                JCheckBox cb = new JCheckBox(f.getAbsolutePath(), true);
                fileChecks.add(cb);
                fileListPanel.add(cb);
            }

            countLabel.setText(tiffs.size() + " file(s) found");
            fileListPanel.revalidate();
            fileListPanel.repaint();
            pack();
        }

        private void runBatchProcess() {
            List<File> selected = new ArrayList<>();
            for (JCheckBox cb : fileChecks) {
                if (cb.isSelected()) selected.add(new File(cb.getText()));
            }
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No files selected.");
                return;
            }

            runBtn.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setMaximum(selected.size());
            progressBar.setValue(0);

            QuantifierParams params   = buildParams();
            int     at        = areaThreshold;
            int     st        = seedThreshold;
            boolean areaEn    = areaEnabled;
            Color   roiColor  = selectedRoiColor();
            boolean seedRoi   = bSaveSeedRoi  .isSelected();
            boolean sizeRoi   = bSaveSizeRoi  .isSelected();
            boolean areaRoi   = bSaveAreaRoi  .isSelected();
            boolean resultRoi = bSaveResultRoi.isSelected();
            boolean resultRoiByObject = bSaveResultRoiByObject.isSelected();
            boolean csv       = bSaveCsv      .isSelected();
            boolean param     = bSaveParam    .isSelected();
            boolean customF   = bCustomFolder .isSelected();
            String  folderPat = bFolderName   .getText();

            // 0 = ask, 1 = overwrite all, 2 = skip all
            int[] overwriteState = {0};

            new SwingWorker<int[], String>() {
                @Override
                protected int[] doInBackground() {
                    int ok = 0, skipped = 0;
                    for (int i = 0; i < selected.size(); i++) {
                        File f = selected.get(i);
                        final int fi = i;
                        publish("Processing " + (fi + 1) + "/" + selected.size() + ": " + f.getName());
                        EventQueue.invokeLater(() -> progressBar.setValue(fi));

                        ImagePlus target = IJ.openImage(f.getAbsolutePath());
                        if (target == null)          { skipped++; IJ.log("Batch SKIP (cannot open): " + f.getName()); continue; }
                        if (target.getNSlices() < 2) { target.close(); skipped++; IJ.log("Batch SKIP (not 3D): " + f.getName()); continue; }

                        String basename = target.getShortTitle().replaceAll("\\.tiff?$", "");
                        String pattern  = customF ? folderPat : "{name} result";
                        String folder   = SeededSpotQuantifier3DSaveSupport.expandFolderTokens(pattern, basename, st, at);
                        File   outDir   = new File(f.getParent(), folder);

                        if (outDir.exists()) {
                            if (overwriteState[0] == 2) {
                                target.close(); skipped++;
                                IJ.log("Batch SKIP (folder exists, skip all): " + f.getName());
                                continue;
                            } else if (overwriteState[0] == 0) {
                                int[] response = {-1};
                                try {
                                    EventQueue.invokeAndWait(() -> {
                                        Object[] opts = {"Overwrite", "Skip", "Overwrite All", "Skip All"};
                                        response[0] = JOptionPane.showOptionDialog(
                                            BatchDialog.this,
                                            "Folder already exists:\n" + outDir.getAbsolutePath(),
                                            "Folder Exists",
                                            JOptionPane.DEFAULT_OPTION,
                                            JOptionPane.QUESTION_MESSAGE,
                                            null, opts, opts[0]);
                                    });
                                } catch (Exception ex) { response[0] = 1; }

                                if (response[0] == 1) { target.close(); skipped++; continue; }      // Skip
                                if (response[0] == 2) { overwriteState[0] = 1; }                    // Overwrite All
                                if (response[0] == 3) { overwriteState[0] = 2; target.close(); skipped++; continue; } // Skip All
                                // 0 = Overwrite once — fall through
                            }
                            // overwriteState[0] == 1 — overwrite all, fall through
                        }

                        String err = SeededSpotQuantifier3DSaveSupport.saveOneToDir(
                            target, target, Math.max(1, target.getC()),
                            at, st, areaEn, params, outDir,
                            seedRoi, sizeRoi, areaRoi, resultRoi, resultRoiByObject,
                            csv, param, roiColor, msg -> publish(msg));
                        target.close();
                        if (err != null) {
                            IJ.log("Batch SKIP (" + err + "): " + f.getName());
                            skipped++;
                        } else {
                            ok++;
                        }
                    }
                    return new int[]{ok, skipped};
                }

                @Override
                protected void process(List<String> chunks) {
                    if (!chunks.isEmpty()) dlgStatus.setText(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    IJ.showStatus("");
                    try {
                        int[] counts = get();
                        progressBar.setValue(selected.size());
                        dlgStatus.setText(counts[0] + " processed, " + counts[1] + " skipped.");
                    } catch (Exception ex) {
                        IJ.error("Batch", "Batch failed: " + ex.getMessage());
                    }
                    runBtn.setEnabled(true);
                }
            }.execute();
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private QuantifierParams buildParams() {
        Double minVol = minVolEnabled ? minVolVal : null;
        Double maxVol = maxVolEnabled ? maxVolVal : null;
        int conn = Integer.parseInt(connectivityChoice.getSelectedItem());
        boolean fillH = fillHolesCheck.getState();
        return new QuantifierParams(areaThreshold, minVol, maxVol,
            false, 1.0, 0.5, conn, fillH);
    }

    private Color selectedSeedPreviewColor() {
        return Color.decode(PREVIEW_COLOR_OPTIONS[seedColorChoice.getSelectedIndex()][1]);
    }

    private Color selectedRoiColor() {
        return Color.decode(PREVIEW_COLOR_OPTIONS[roiColorChoice.getSelectedIndex()][1]);
    }

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

    private static int countLabels(SegmentationResult3D seg) {
        if (seg == null || seg.labelImage == null) return 0;
        TreeSet<Integer> labels = new TreeSet<>();
        int d = seg.labelImage.getNSlices();
        for (int z = 1; z <= d; z++) {
            ImageProcessor ip = seg.labelImage.getStack().getProcessor(z);
            int w = ip.getWidth();
            int h = ip.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int label = (int) Math.round(ip.getPixelValue(x, y));
                    if (label > 0) labels.add(label);
                }
            }
        }
        return labels.size();
    }

    private void setStatusText(String text) {
        statusLabel.setText(text);
        IJ.showStatus(text == null || text.isEmpty()
            ? ""
            : "Seeded Spot Quantifier 3D: " + text);
    }

    private static void reportProgress(Consumer<String> progress, String message) {
        if (progress != null) progress.accept(message);
    }

    // =========================================================
    // Z-watch
    // =========================================================

    private void startZWatch() {
        lastZ = currentZPlane();
        zWatchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                int z = currentZPlane();
                if (z != lastZ) {
                    lastZ = z;
                    EventQueue.invokeLater(() -> updatePreviewForZChange());
                }
            }
        }, 300, 300);
    }

    private void placeNearImage() {
        Window active = rawImp != null ? rawImp.getWindow() : WindowManager.getCurrentWindow();
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
