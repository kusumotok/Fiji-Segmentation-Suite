package jp.yourorg.fiji_maxima_based_segmenter.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.alg.QuantifierParams;
import jp.yourorg.fiji_maxima_based_segmenter.alg.SeededQuantifier3D;
import jp.yourorg.fiji_maxima_based_segmenter.alg.SegmentationResult3D;
import jp.yourorg.fiji_maxima_based_segmenter.core.ThresholdModel;
import jp.yourorg.fiji_maxima_based_segmenter.util.SeededSpotQuantifier3DImageSupport;
import jp.yourorg.fiji_maxima_based_segmenter.util.SeededSpotQuantifier3DSaveSupport;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

public class SeededSpotQuantifier3DMultiFrame extends PlugInFrame {
    private static final String NONE_ITEM = SeededSpotQuantifier3DImageSupport.NONE_ITEM;
    private static final int VOL_SLIDER_STEPS = 1000;

    private final ThresholdModel model;
    private ImagePlus representativeRawImp;
    private ImagePlus representativeImp;
    private boolean ownsRepresentativeImp;

    private final HistogramPanel histogramPanel;
    private final Button windowSelectBtn = new Button("Windows...");
    private final Choice channelChoice = new Choice();

    private final Checkbox areaEnabledCheck;
    private final Scrollbar areaThreshBar;
    private final TextField areaThreshField;
    private final Scrollbar seedThreshBar;
    private final TextField seedThreshField;
    private final Checkbox minVolCheck;
    private final Scrollbar minVolBar;
    private final TextField minVolField;
    private final Checkbox maxVolCheck;
    private final Scrollbar maxVolBar;
    private final TextField maxVolField;
    private final Choice connectivityChoice;
    private final Checkbox fillHolesCheck;

    private final CheckboxGroup previewGroup = new CheckboxGroup();
    private final Checkbox previewOff;
    private final Checkbox previewOverlay;
    private final Checkbox previewRoi;

    private final Button applyBtn = new Button("Apply");
    private final Button saveAllBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Checkbox saveSeedRoiCheck   = new Checkbox("Seed ROI",   false);
    private final Checkbox saveSizeRoiCheck   = new Checkbox("Size ROI",   false);
    private final Checkbox saveAreaRoiCheck   = new Checkbox("Area ROI",   false);
    private final Checkbox saveResultRoiCheck = new Checkbox("Result ROI", true);
    private final Checkbox saveCsvCheck       = new Checkbox("CSV",        true);
    private final Checkbox saveParamCheck     = new Checkbox("Param",      true);
    private final Checkbox customFolderCheck  = new Checkbox("Custom folder name:", false);
    private final TextField folderNameField   = new TextField("{name} result", 22);
    private final Button    saveToExecBtn     = new Button("Save to...");
    private final Button saveToggleBtn        = new Button("\u25bc Save options");
    private boolean saveSectionExpanded = true;
    private Panel saveOptionsPanel;
    private Panel saveChecksGrid;
    private Panel rightCenterPanel;

    private final Choice seedColorChoice;
    private final Choice roiColorChoice;
    private final TextField overlayOpacityField = new TextField("50", 3);
    private final Label statusLabel = new Label("", Label.LEFT);

    private final JPanel imagesContentPanel = new JPanel(new GridBagLayout());
    private JDialog selectorDialog;
    private Point selectorLocation;
    private Dimension selectorSize;
    private boolean selectorOpenedOnce = false;

    private final List<TargetRow> targetRows = new ArrayList<TargetRow>();
    private boolean syncing = false;
    private boolean areaEnabled = true;
    private int areaThreshold;
    private int seedThreshold;
    private boolean minVolEnabled = true;
    private boolean maxVolEnabled = false;
    private double minVolVal = 0.1;
    private double maxVolVal = 50.0;
    private double volRangeMin = 0.01;
    private double volRangeMax = 100.0;
    private int selectedCh = 1;

    private final Timer zWatchTimer = new Timer("ssq3d-multi-zwatch", true);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicInteger applyGeneration = new AtomicInteger();
    private SwingWorker<?, ?> activeWorker;
    private boolean operationRunning = false;
    private boolean cleanupDone = false;

    public SeededSpotQuantifier3DMultiFrame() {
        super("Seeded Spot Quantifier 3D Multi");

        representativeImp = IJ.createImage("ssq3d-multi-none", "16-bit black", 1, 1, 2);
        ownsRepresentativeImp = true;
        model = ThresholdModel.createFor3DPlugin(representativeImp);
        int imgMin = model.getMinValue();
        int imgMax = safeImgMax(imgMin, model.getMaxValue());
        areaThreshold = model.getTBg();
        seedThreshold = Math.min(imgMax, Math.max(areaThreshold, areaThreshold * 2));
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);

        areaEnabledCheck = new Checkbox("", areaEnabled);
        areaThreshBar = new Scrollbar(Scrollbar.HORIZONTAL, areaThreshold, 1, imgMin, imgMax + 1);
        areaThreshField = new TextField(Integer.toString(areaThreshold), 7);
        seedThreshBar = new Scrollbar(Scrollbar.HORIZONTAL, seedThreshold, 1, imgMin, imgMax + 1);
        seedThreshField = new TextField(Integer.toString(seedThreshold), 7);
        minVolCheck = new Checkbox("", minVolEnabled);
        minVolBar = makeVolBar(minVolVal);
        minVolField = new TextField(formatVol(minVolVal), 7);
        maxVolCheck = new Checkbox("", maxVolEnabled);
        maxVolBar = makeVolBar(maxVolVal);
        maxVolBar.setEnabled(maxVolEnabled);
        maxVolField = new TextField(formatVol(maxVolVal), 7);
        maxVolField.setEnabled(maxVolEnabled);

        connectivityChoice = new Choice();
        connectivityChoice.add("6");
        connectivityChoice.add("18");
        connectivityChoice.add("26");
        connectivityChoice.select("6");
        fillHolesCheck = new Checkbox("Fill holes", false);

        channelChoice.add("1");

        previewOff = new Checkbox("Off", previewGroup, true);
        previewOverlay = new Checkbox("Overlay", previewGroup, false);
        previewRoi = new Checkbox("ROI", previewGroup, false);

        seedColorChoice = new Choice();
        roiColorChoice = new Choice();
        for (String[] entry : SeededSpotQuantifier3DFrame.PREVIEW_COLOR_OPTIONS) {
            seedColorChoice.add(entry[0]);
            roiColorChoice.add(entry[0]);
        }
        seedColorChoice.select(1);
        roiColorChoice.select(0);

        histogramPanel = new HistogramPanel(representativeImp, model, this::onHistogramThresholds);
        histogramPanel.setFgEnabled(true);

        buildLayout();
        wireEvents();
        refreshTargetRows();
        startZWatch();
        updateControlStates();
        validate();
        pack();
        placeNearActiveWindow();
    }

    private void buildLayout() {
        setLayout(new BorderLayout(6, 6));
        add(buildRightPanel(), BorderLayout.CENTER);

        Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        buttons.add(applyBtn);
        buttons.add(saveAllBtn);
        buttons.add(saveToExecBtn);
        buttons.add(cancelBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private JPanel buildImagesHeader() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 2, 2, 2);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        p.add(new JLabel("Use"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(new JLabel("3D image"), c);
        c.gridx = 2;
        c.weightx = 0.4;
        p.add(new JLabel("Z-proj"), c);
        return p;
    }

    private Component buildRightPanel() {
        Panel p = new Panel(new BorderLayout(4, 4));
        Panel top = new Panel(new BorderLayout(4, 4));
        top.add(makeChannelRow(), BorderLayout.NORTH);
        top.add(histogramPanel, BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);

        rightCenterPanel = new Panel(new GridBagLayout());
        int row = 0;
        addRightRow(makeThreshRow("Seed threshold:", seedThreshBar, seedThreshField), row++);
        addRightRow(makeVolRow("Min vol \u00b5m\u00b3 (seed):", minVolCheck, minVolBar, minVolField), row++);
        addRightRow(makeVolRow("Max vol \u00b5m\u00b3 (seed):", maxVolCheck, maxVolBar, maxVolField), row++);
        addRightRow(makeAreaThreshRow(), row++);
        addRightRow(makeConnectivityRow(), row++);
        addRightRow(makePreviewRow(), row++);
        addRightRow(makeColorsRow(), row++);
        addRightRow(makeSaveToggleRow(), row++);
        saveOptionsPanel = makeSaveOptionsPanel();
        addRightRow(saveOptionsPanel, row++);
        addRightRow(makeStatusRow(), row);
        p.add(rightCenterPanel, BorderLayout.CENTER);
        return p;
    }

    private Panel makeChannelRow() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(windowSelectBtn, c);

        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(new Label("Ch:"), c);

        c.gridx = 1;
        c.weightx = 0.35;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(channelChoice, c);
        return p;
    }

    private void addRightRow(Component comp, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(1, 0, 1, 0);
        rightCenterPanel.add(comp, c);
    }

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

    private Panel makeSliderFieldRow(Scrollbar bar, TextField field) {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 4, 0, 2);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(bar, c);
        c.gridx = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
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

    private Panel makePreviewRow() {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.add(new Label("Preview:"));
        p.add(previewOff);
        p.add(previewOverlay);
        p.add(previewRoi);
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
        Button selectAllBtn = new Button("Select All");
        Button deselectAllBtn = new Button("Deselect All");
        selectAllBtn.addActionListener(e -> setSaveChecks(true));
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

    private Panel makeStatusRow() {
        Panel p = new Panel(new BorderLayout());
        statusLabel.setForeground(new Color(80, 80, 80));
        Font baseFont = statusLabel.getFont();
        if (baseFont == null) baseFont = p.getFont();
        if (baseFont == null) baseFont = new Font("Dialog", Font.PLAIN, 12);
        statusLabel.setFont(baseFont);
        FontMetrics fm = statusLabel.getFontMetrics(baseFont);
        int prefW = fm.stringWidth("10/10 saving very_long_image_name.tif") + 16;
        int prefH = fm.getHeight() + 4;
        statusLabel.setPreferredSize(new Dimension(prefW, prefH));
        p.add(statusLabel, BorderLayout.CENTER);
        return p;
    }

    private void wireEvents() {
        windowSelectBtn.addActionListener(e -> toggleSelectorDialog());

        channelChoice.addItemListener(e -> {
            if (syncing || e.getStateChange() != ItemEvent.SELECTED) return;
            selectedCh = channelChoice.getSelectedIndex() + 1;
            updateRepresentativeImage();
            updateThresholdRangeFromSelection();
            onParamsChanged();
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
        fillHolesCheck.addItemListener(e -> onParamsChanged());

        ItemListener previewListener = e -> onPreviewModeChanged();
        previewOff.addItemListener(previewListener);
        previewOverlay.addItemListener(previewListener);
        previewRoi.addItemListener(previewListener);
        seedColorChoice.addItemListener(e -> rerenderAppliedOverlays());
        roiColorChoice.addItemListener(e -> rerenderAppliedOverlays());
        customFolderCheck.addItemListener(e -> updateControlStates());
        overlayOpacityField.addActionListener(e -> rerenderAppliedOverlays());
        overlayOpacityField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { rerenderAppliedOverlays(); }
        });

        saveToggleBtn.addActionListener(e -> toggleSaveSection());
        applyBtn.addActionListener(e -> runApply());
        saveAllBtn.addActionListener(e -> runSaveAll());
        saveToExecBtn.addActionListener(e -> runSaveTo());
        cancelBtn.addActionListener(e -> cancelCurrentOperation());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                updateSaveChecksColumns();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    @Override
    public void dispose() {
        cleanupResources();
        super.dispose();
    }

    private void cleanupResources() {
        if (cleanupDone) return;
        cleanupDone = true;
        applyGeneration.incrementAndGet();
        clearAllOverlays();
        zWatchTimer.cancel();
        closeSelectorDialog();
        for (TargetRow row : targetRows) row.clearPreviewResult();
        targetRows.clear();
        imagesContentPanel.removeAll();
        ImagePlus oldRepresentative = representativeImp;
        boolean oldOwned = ownsRepresentativeImp;
        representativeImp = null;
        representativeRawImp = null;
        ownsRepresentativeImp = false;
        SeededSpotQuantifier3DImageSupport.disposeProcessingImage(oldRepresentative, oldOwned);
    }

    private void toggleSelectorDialog() {
        if (selectorDialog != null && selectorDialog.isDisplayable() && selectorDialog.isVisible()) {
            closeSelectorDialog();
        } else {
            openSelectorDialog();
        }
    }

    private void openSelectorDialog() {
        if (selectorDialog == null || !selectorDialog.isDisplayable()) {
            selectorDialog = new JDialog(this, "Window Select", false);
            selectorDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            selectorDialog.setContentPane(buildSelectorContent());
            selectorDialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { storeSelectorBounds(); }
                @Override public void windowClosed(WindowEvent e) {
                    storeSelectorBounds();
                    selectorDialog = null;
                }
            });
            if (selectorOpenedOnce && selectorLocation != null && selectorSize != null) {
                selectorDialog.setBounds(selectorLocation.x, selectorLocation.y, selectorSize.width, selectorSize.height);
            } else {
                selectorDialog.pack();
                placeSelectorLeftOfMain(selectorDialog);
                selectorOpenedOnce = true;
            }
        } else {
            selectorDialog.setContentPane(buildSelectorContent());
            selectorDialog.validate();
        }
        selectorDialog.setVisible(true);
        selectorDialog.toFront();
    }

    private void closeSelectorDialog() {
        if (selectorDialog == null) return;
        storeSelectorBounds();
        selectorDialog.dispose();
        selectorDialog = null;
    }

    private void storeSelectorBounds() {
        if (selectorDialog == null) return;
        selectorLocation = selectorDialog.getLocation();
        selectorSize = selectorDialog.getSize();
    }

    private JPanel buildSelectorContent() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton refreshSwingBtn = new JButton("Refresh");
        JButton selectAllBtn = new JButton("Select All");
        JButton deselectAllBtn = new JButton("Deselect All");
        refreshSwingBtn.addActionListener(e -> refreshTargetRows());
        selectAllBtn.addActionListener(e -> setAllTargetSelection(true));
        deselectAllBtn.addActionListener(e -> setAllTargetSelection(false));
        controls.add(refreshSwingBtn);
        controls.add(selectAllBtn);
        controls.add(deselectAllBtn);

        root.add(controls, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(buildImagesHeader(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(imagesContentPanel);
        scroll.setPreferredSize(selectorSize != null ? selectorSize : new Dimension(360, 320));
        center.add(scroll, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private void placeSelectorLeftOfMain(Window selector) {
        Point p;
        try { p = getLocationOnScreen(); } catch (IllegalComponentStateException ex) { return; }
        int x = p.x - selector.getWidth() - 8;
        int y = p.y;
        Rectangle screen = getGraphicsConfiguration() != null
            ? getGraphicsConfiguration().getBounds()
            : new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
        if (x < screen.x) x = p.x + getWidth() + 8;
        if (y + selector.getHeight() > screen.y + screen.height) {
            y = Math.max(screen.y, screen.y + screen.height - selector.getHeight());
        }
        selector.setLocation(x, y);
    }

    private void toggleSaveSection() {
        int currentWidth = getWidth();
        saveSectionExpanded = !saveSectionExpanded;
        saveOptionsPanel.setVisible(saveSectionExpanded);
        saveToggleBtn.setLabel(saveSectionExpanded ? "\u25bc Save options" : "\u25b6 Save options");
        updateSaveChecksColumns();
        rightCenterPanel.validate();
        pack();
        setSize(currentWidth, getHeight());
    }

    private void setSaveChecks(boolean state) {
        saveSeedRoiCheck.setState(state);
        saveSizeRoiCheck.setState(state);
        saveAreaRoiCheck.setState(state);
        saveResultRoiCheck.setState(state);
        saveCsvCheck.setState(state);
        saveParamCheck.setState(state);
    }

    private File chooseSaveDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select save folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File dir = chooser.getSelectedFile();
        if (dir == null) return null;
        return dir;
    }

    private void runSaveTo() {
        File explicitDir = chooseSaveDirectory();
        if (explicitDir == null) return;
        runSaveAll(explicitDir);
    }

    private void setAllTargetSelection(boolean state) {
        for (TargetRow row : targetRows) row.useCheck.setSelected(state);
        updateChannelChoiceItems();
        updateRepresentativeImage();
        updateThresholdRangeFromSelection();
        updateControlStates();
    }

    private void refreshTargetRows() {
        Map<ImagePlus, TargetRow> previous = new HashMap<ImagePlus, TargetRow>();
        for (TargetRow row : targetRows) previous.put(row.rawImp, row);

        List<ImagePlus> open3d = SeededSpotQuantifier3DImageSupport.listOpen3DImages();
        List<ImagePlus> open2d = SeededSpotQuantifier3DImageSupport.listOpen2DImages();

        targetRows.clear();
        imagesContentPanel.removeAll();
        int rowIndex = 0;
        for (ImagePlus raw : open3d) {
            TargetRow old = previous.get(raw);
            TargetRow row = new TargetRow(raw);
            row.useCheck.setSelected(old == null || old.useCheck.isSelected());
            populateZProjChoices(row.zProjChoice, open2d);
            String selectedTitle = old != null ? old.getSelectedZProjTitle() : null;
            if (selectedTitle == null || NONE_ITEM.equals(selectedTitle)) {
                selectedTitle = SeededSpotQuantifier3DImageSupport.autoMatchZProjTitle(raw, open2d);
            }
            selectComboItem(row.zProjChoice, selectedTitle);
            targetRows.add(row);
            addTargetRow(row, rowIndex++);
        }

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = rowIndex;
        filler.gridwidth = 3;
        filler.weightx = 1.0;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.BOTH;
        imagesContentPanel.add(new JPanel(), filler);

        imagesContentPanel.revalidate();
        imagesContentPanel.repaint();
        updateChannelChoiceItems();
        updateRepresentativeImage();
        updateThresholdRangeFromSelection();
        updateControlStates();
        if (targetRows.isEmpty()) setStatusText("No 3D images open.");
        else if (selectedTargets().isEmpty()) setStatusText("Select at least one 3D image.");
        else setStatusText("Press Apply to update");
    }

    private void addTargetRow(TargetRow row, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(1, 2, 1, 2);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        imagesContentPanel.add(row.useCheck, c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        imagesContentPanel.add(row.titleLabel, c);
        c.gridx = 2;
        c.weightx = 0.3;
        imagesContentPanel.add(row.zProjChoice, c);

        row.useCheck.addActionListener(e -> {
            updateChannelChoiceItems();
            updateRepresentativeImage();
            updateThresholdRangeFromSelection();
            updateControlStates();
        });
        row.zProjChoice.addActionListener(e -> {
            row.clearZProjCache();
            if (row.previewResult != null && !previewOff.getState()) renderPreviewForRow(row, row.previewResult);
        });
    }

    private void populateZProjChoices(JComboBox<String> combo, List<ImagePlus> candidates) {
        combo.removeAllItems();
        combo.addItem(NONE_ITEM);
        for (ImagePlus img : candidates) combo.addItem(img.getTitle());
    }

    private void selectComboItem(JComboBox<String> combo, String title) {
        if (title == null) {
            combo.setSelectedItem(NONE_ITEM);
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (title.equals(combo.getItemAt(i))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedItem(NONE_ITEM);
    }

    private void updateRepresentativeImage() {
        TargetRow selected = firstSelectedTarget();
        ImagePlus oldImp = representativeImp;
        boolean oldOwned = ownsRepresentativeImp;

        representativeRawImp = selected != null ? selected.rawImp : null;
        if (representativeRawImp != null) {
            representativeImp = SeededSpotQuantifier3DImageSupport.extractProcessingImage(representativeRawImp, selectedCh);
            ownsRepresentativeImp = representativeImp != representativeRawImp;
        } else {
            representativeImp = IJ.createImage("ssq3d-multi-none", "16-bit black", 1, 1, 2);
            ownsRepresentativeImp = true;
        }

        if (oldImp != representativeImp) {
            SeededSpotQuantifier3DImageSupport.disposeProcessingImage(oldImp, oldOwned);
        }
        model.setImage(representativeImp);
        histogramPanel.setImage(representativeImp);
    }

    private void updateChannelChoiceItems() {
        int maxChannels = 1;
        List<TargetRow> source = selectedTargets();
        if (source.isEmpty()) source = targetRows;
        for (TargetRow row : source) maxChannels = Math.max(maxChannels, Math.max(1, row.rawImp.getNChannels()));
        syncing = true;
        channelChoice.removeAll();
        for (int ch = 1; ch <= maxChannels; ch++) channelChoice.add(Integer.toString(ch));
        selectedCh = Math.max(1, Math.min(selectedCh, maxChannels));
        channelChoice.select(selectedCh - 1);
        syncing = false;
    }

    private void updateThresholdRangeFromSelection() {
        List<TargetRow> selected = selectedTargets();
        if (selected.isEmpty()) return;

        Integer min = null;
        Integer max = null;
        for (TargetRow row : selected) {
            if (selectedCh > Math.max(1, row.rawImp.getNChannels())) continue;
            ImagePlus proc = SeededSpotQuantifier3DImageSupport.extractProcessingImage(row.rawImp, selectedCh);
            boolean owned = proc != row.rawImp;
            try {
                int[] minMax = SeededSpotQuantifier3DImageSupport.computeStackMinMax(proc);
                int imgMin = minMax[0];
                int imgMax = minMax[1];
                min = min == null ? imgMin : Math.min(min, imgMin);
                max = max == null ? imgMax : Math.max(max, imgMax);
            } finally {
                SeededSpotQuantifier3DImageSupport.disposeProcessingImage(proc, owned);
            }
        }
        if (min == null || max == null) {
            setStatusText("No selected image has channel " + selectedCh + ".");
            return;
        }
        int imgMin = min;
        int imgMax = safeImgMax(imgMin, max);
        model.setTBg(areaThreshold);
        model.setTFg(seedThreshold);
        syncing = true;
        areaThreshBar.setValues(areaThreshold, 1, imgMin, imgMax + 1);
        seedThreshBar.setValues(seedThreshold, 1, imgMin, imgMax + 1);
        areaThreshField.setText(Integer.toString(areaThreshold));
        seedThreshField.setText(Integer.toString(seedThreshold));
        histogramPanel.repaint();
        syncing = false;
    }

    private void updateControlStates() {
        boolean hasSelected = !selectedTargets().isEmpty();
        histogramPanel.setEnabled(hasSelected);
        areaEnabledCheck.setEnabled(hasSelected);
        areaThreshBar.setEnabled(hasSelected && areaEnabled);
        areaThreshField.setEnabled(hasSelected && areaEnabled);
        seedThreshBar.setEnabled(hasSelected);
        seedThreshField.setEnabled(hasSelected);
        minVolCheck.setEnabled(hasSelected);
        minVolBar.setEnabled(hasSelected && minVolEnabled);
        minVolField.setEnabled(hasSelected && minVolEnabled);
        maxVolCheck.setEnabled(hasSelected);
        maxVolBar.setEnabled(hasSelected && maxVolEnabled);
        maxVolField.setEnabled(hasSelected && maxVolEnabled);
        connectivityChoice.setEnabled(hasSelected);
        fillHolesCheck.setEnabled(hasSelected);
        previewOff.setEnabled(hasSelected);
        previewOverlay.setEnabled(hasSelected);
        previewRoi.setEnabled(hasSelected);
        seedColorChoice.setEnabled(hasSelected);
        roiColorChoice.setEnabled(hasSelected);
        overlayOpacityField.setEnabled(hasSelected);
        saveToggleBtn.setEnabled(hasSelected);
        saveSeedRoiCheck.setEnabled(hasSelected);
        saveSizeRoiCheck.setEnabled(hasSelected);
        saveAreaRoiCheck.setEnabled(hasSelected);
        saveResultRoiCheck.setEnabled(hasSelected);
        saveCsvCheck.setEnabled(hasSelected);
        saveParamCheck.setEnabled(hasSelected);
        customFolderCheck.setEnabled(hasSelected);
        folderNameField.setEnabled(hasSelected && customFolderCheck.getState());
        saveToExecBtn.setEnabled(hasSelected && !operationRunning);
        applyBtn.setEnabled(hasSelected && !operationRunning);
        saveAllBtn.setEnabled(hasSelected && !operationRunning);
        cancelBtn.setEnabled(operationRunning);
    }

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

    private void onParamsChanged() {
        for (TargetRow row : targetRows) row.clearPreviewResult();
        if (previewOff.getState()) setStatusText("");
        else setStatusText("Press Apply to update");
    }

    private void onPreviewModeChanged() {
        if (previewOff.getState()) {
            clearAllOverlays();
            setStatusText("Preview off.");
        } else if (hasPreviewResults()) {
            rerenderAppliedOverlays();
        } else {
            setStatusText("Press Apply to update");
        }
    }

    private void rerenderAppliedOverlays() {
        if (previewOff.getState()) return;
        for (TargetRow row : targetRows) {
            if (row.previewResult != null) renderPreviewForRow(row, row.previewResult);
        }
    }

    private void runApply() {
        final List<TargetRow> selected = selectedTargets();
        if (selected.isEmpty()) {
            setStatusText("Select at least one 3D image.");
            return;
        }
        if (previewOff.getState()) {
            clearAllOverlays();
            setStatusText("Preview off.");
            return;
        }

        final int generation = applyGeneration.incrementAndGet();
        final QuantifierParams params = buildParams();
        final int at = areaThreshold;
        final int st = seedThreshold;
        final boolean areaEn = areaEnabled;
        final boolean roiMode = previewRoi.getState();

        beginOperation();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setStatusText("Applying preview...");

        activeWorker = new SwingWorker<PreviewSummary, Runnable>() {
            @Override
            protected PreviewSummary doInBackground() {
                PreviewSummary summary = new PreviewSummary();
                for (TargetRow row : selected) {
                    if (isCancelled() || cancelRequested.get() || generation != applyGeneration.get()) break;
                    publish(() -> setStatusText("Applying " + row.rawImp.getShortTitle() + "..."));
                    if (selectedCh > Math.max(1, row.rawImp.getNChannels())) {
                        summary.channelMismatchCount++;
                        row.clearPreviewResult();
                        publish(() -> clearOverlay(row));
                        continue;
                    }

                    ImagePlus proc = SeededSpotQuantifier3DImageSupport.extractProcessingImage(row.rawImp, selectedCh);
                    boolean owned = proc != row.rawImp;
                    try {
                        publish(() -> setStatusText("Applying " + row.rawImp.getShortTitle() + ": extracting channel..."));
                        SeededQuantifier3D.SeededResult result = SeededQuantifier3D.compute(
                            proc, at, st, params, computeVoxelVol(proc), areaEn,
                            stage -> publish(() -> setStatusText(
                                "Applying " + row.rawImp.getShortTitle() + ": " + stage + "...")),
                            () -> isCancelled() || cancelRequested.get() || generation != applyGeneration.get());
                        row.previewResult = result;
                        row.lastRenderedZ = currentZPlane(row.rawImp);
                        if (result == null || SeededSpotQuantifier3DImageSupport.countLabels(result.finalSeg) == 0) {
                            summary.emptyCount++;
                            publish(() -> clearOverlay(row));
                            continue;
                        }
                        summary.updatedCount++;
                        if (row.getZProjImage() != null) summary.zProjUpdatedCount++;
                        publish(() -> {
                            if (generation != applyGeneration.get()) return;
                            setStatusText("Applying " + row.rawImp.getShortTitle() + ": rendering current Z overlay...");
            if (roiMode) {
                renderRoiOverlay(row, result.seedSeg, result.finalSeg, areaEn,
                    () -> setStatusText("Applying " + row.rawImp.getShortTitle() + ": building Z-proj ROI overlay..."),
                    stage -> setStatusText("Applying " + row.rawImp.getShortTitle() + ": " + stage + "..."));
            } else {
                renderOverlay(row, result.seedSeg, result.finalSeg, areaEn,
                    () -> setStatusText("Applying " + row.rawImp.getShortTitle() + ": building Z-proj overlay..."));
            }
                        });
                    } catch (CancellationException ex) {
                        break;
                    } catch (Exception ex) {
                        summary.failedCount++;
                        summary.failedImages.add(row.rawImp.getShortTitle());
                        row.clearPreviewResult();
                        publish(() -> clearOverlay(row));
                    } finally {
                        SeededSpotQuantifier3DImageSupport.disposeProcessingImage(proc, owned);
                    }
                }
                return summary;
            }

            @Override
            protected void process(List<Runnable> chunks) {
                for (Runnable chunk : chunks) chunk.run();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    PreviewSummary summary = get();
                    setStatusText(cancelRequested.get() ? "Cancelled." : summary.toStatus());
                } catch (CancellationException ex) {
                    setStatusText("Cancelled.");
                } catch (Exception ex) {
                    setStatusText("Apply failed: " + ex.getMessage());
                } finally {
                    activeWorker = null;
                    endOperation();
                }
            }
        };
        activeWorker.execute();
    }

    private void runSaveAll() {
        runSaveAll(null);
    }

    private void runSaveAll(final File explicitDir) {
        final List<TargetRow> selected = selectedTargets();
        if (selected.isEmpty()) {
            setStatusText("Select at least one 3D image.");
            return;
        }

        final QuantifierParams params = buildParams();
        final boolean saveSeedRoi = saveSeedRoiCheck.getState();
        final boolean saveSizeRoi = saveSizeRoiCheck.getState();
        final boolean saveAreaRoi = saveAreaRoiCheck.getState();
        final boolean saveResultRoi = saveResultRoiCheck.getState();
        final boolean saveCsv = saveCsvCheck.getState();
        final boolean saveParam = saveParamCheck.getState();
        final boolean customFolder = customFolderCheck.getState();
        final String folderPattern = folderNameField.getText();
        final Color roiColor = selectedRoiColor();
        final int at = areaThreshold;
        final int st = seedThreshold;
        final boolean areaEn = areaEnabled;

        beginOperation();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setStatusText("Saving: preparing...");

        activeWorker = new SwingWorker<SaveSummary, Runnable>() {
            @Override
            protected SaveSummary doInBackground() {
                SaveSummary summary = new SaveSummary(selected.size());
                for (int i = 0; i < selected.size(); i++) {
                    if (isCancelled() || cancelRequested.get()) break;
                    final int idx = i + 1;
                    final TargetRow row = selected.get(i);
                    if (selectedCh > Math.max(1, row.rawImp.getNChannels())) {
                        summary.failed++;
                        summary.failedImages.add(row.rawImp.getShortTitle() + " (channel mismatch)");
                        continue;
                    }

                    File outDir = SeededSpotQuantifier3DSaveSupport.resolveOutputDir(
                        SeededSpotQuantifier3DMultiFrame.this, row.rawImp, customFolder, folderPattern, st, at, explicitDir);
                    if (outDir == null) {
                        summary.failed++;
                        summary.failedImages.add(row.rawImp.getShortTitle() + " (no output folder)");
                        continue;
                    }

                    if (outDir.exists()) {
                        int choice = JOptionPane.showOptionDialog(
                            SeededSpotQuantifier3DMultiFrame.this,
                            "Folder already exists:\n" + outDir.getAbsolutePath() + "\nOverwrite?",
                            "Folder Exists",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            new Object[]{"Overwrite", "Cancel"},
                            "Cancel");
                        if (choice != 0) {
                            summary.failed++;
                            summary.failedImages.add(row.rawImp.getShortTitle() + " (cancelled)");
                            continue;
                        }
                    }

                    ImagePlus proc = SeededSpotQuantifier3DImageSupport.extractProcessingImage(row.rawImp, selectedCh);
                    boolean owned = proc != row.rawImp;
                    try {
                        final String prefix = idx + "/" + selected.size() + " " + row.rawImp.getShortTitle() + " - ";
                        String err = SeededSpotQuantifier3DSaveSupport.saveOneToDir(
                            proc, row.rawImp, selectedCh, at, st, areaEn, params, outDir,
                            saveSeedRoi, saveSizeRoi, saveAreaRoi, saveResultRoi, saveCsv, saveParam, roiColor,
                            msg -> publish(() -> setStatusText(prefix + trimSavingPrefix(msg))),
                            () -> isCancelled() || cancelRequested.get());
                        if (SeededSpotQuantifier3DSaveSupport.CANCELLED.equals(err)) {
                            break;
                        } else if (err == null) {
                            summary.ok++;
                        } else {
                            summary.failed++;
                            summary.failedImages.add(row.rawImp.getShortTitle() + " (" + err + ")");
                            final String errMsg = err;
                            publish(() -> JOptionPane.showMessageDialog(
                                SeededSpotQuantifier3DMultiFrame.this,
                                row.rawImp.getShortTitle() + ": " + errMsg,
                                "Save failed", JOptionPane.ERROR_MESSAGE));
                        }
                    } finally {
                        SeededSpotQuantifier3DImageSupport.disposeProcessingImage(proc, owned);
                    }
                }
                return summary;
            }

            @Override
            protected void process(List<Runnable> chunks) {
                for (Runnable chunk : chunks) chunk.run();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    SaveSummary summary = get();
                    setStatusText(cancelRequested.get() ? "Cancelled." : summary.toStatus());
                } catch (CancellationException ex) {
                    setStatusText("Cancelled.");
                } catch (Exception ex) {
                    setStatusText("Save failed: " + ex.getMessage());
                } finally {
                    activeWorker = null;
                    endOperation();
                }
            }
        };
        activeWorker.execute();
    }

    private static String trimSavingPrefix(String msg) {
        return msg.startsWith("Saving: ") ? msg.substring("Saving: ".length()) : msg;
    }

    private void beginOperation() {
        cancelRequested.set(false);
        operationRunning = true;
        updateControlStates();
    }

    private void endOperation() {
        operationRunning = false;
        cancelRequested.set(false);
        updateControlStates();
    }

    private void cancelCurrentOperation() {
        if (!operationRunning) return;
        cancelRequested.set(true);
        setStatusText("Cancelling...");
        applyGeneration.incrementAndGet();
        if (activeWorker != null) activeWorker.cancel(true);
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

    private void renderPreviewForRow(TargetRow row, SeededQuantifier3D.SeededResult result) {
        if (result == null) {
            clearOverlay(row);
            return;
        }
        if (previewRoi.getState()) renderRoiOverlay(row, result.seedSeg, result.finalSeg, areaEnabled);
        else renderOverlay(row, result.seedSeg, result.finalSeg, areaEnabled);
    }

    private void renderOverlay(TargetRow row, SegmentationResult3D seedSeg, SegmentationResult3D finalSeg, boolean areaEn) {
        renderOverlay(row, seedSeg, finalSeg, areaEn, null);
    }

    private void renderOverlay(TargetRow row, SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                               boolean areaEn, Runnable beforeZProj) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int zPlane = currentZPlane(row.rawImp);
        int nSlices = finalSeg.labelImage.getNSlices();
        if (zPlane < 1 || zPlane > nSlices) return;

        ImageProcessor finalIp = finalSeg.labelImage.getStack().getProcessor(zPlane);
        int w = finalIp.getWidth();
        int h = finalIp.getHeight();
        ImageProcessor seedIp = (areaEn && seedSeg != null && seedSeg.labelImage != null
            && zPlane <= seedSeg.labelImage.getNSlices())
            ? seedSeg.labelImage.getStack().getProcessor(zPlane) : null;

        int seedRgb = toRgbSolid(selectedSeedPreviewColor());
        int areaRgb = toRgbSolid(selectedRoiColor());
        ColorProcessor cp = new ColorProcessor(w, h);
        int[] pixels = (int[]) cp.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean isSeed = seedIp != null && (int) Math.round(seedIp.getPixelValue(x, y)) > 0;
                int finalLabel = (int) Math.round(finalIp.getPixelValue(x, y));
                if (isSeed) pixels[y * w + x] = seedRgb;
                else if (finalLabel > 0) pixels[y * w + x] = areaRgb;
            }
        }

        ImageRoi iroi = new ImageRoi(0, 0, cp);
        iroi.setZeroTransparent(true);
        iroi.setOpacity(getOverlayOpacity());
        Overlay overlay = new Overlay();
        overlay.add(iroi);
        row.rawImp.setOverlay(overlay);
        row.rawImp.updateAndDraw();
        row.lastRenderedZ = zPlane;

        ImagePlus zProj = row.getZProjImage();
        if (zProj != null) {
            if (beforeZProj != null) beforeZProj.run();
            renderOverlayOnZProj(zProj, seedSeg, finalSeg, areaEn);
        }
    }

    private void renderRoiOverlay(TargetRow row, SegmentationResult3D seedSeg, SegmentationResult3D finalSeg, boolean areaEn) {
        renderRoiOverlay(row, seedSeg, finalSeg, areaEn, null, null);
    }

    private void renderRoiOverlay(TargetRow row, SegmentationResult3D seedSeg, SegmentationResult3D finalSeg,
                                  boolean areaEn, Runnable beforeZProj, java.util.function.Consumer<String> progress) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int zPlane = currentZPlane(row.rawImp);
        int nSlices = finalSeg.labelImage.getNSlices();
        if (zPlane < 1 || zPlane > nSlices) return;

        Overlay overlay = new Overlay();
        if (areaEn && seedSeg != null && seedSeg.labelImage != null && zPlane <= seedSeg.labelImage.getNSlices()) {
            addLabelOutlines(seedSeg.labelImage.getStack().getProcessor(zPlane), selectedSeedPreviewColor(), overlay);
        }
        addLabelOutlines(finalSeg.labelImage.getStack().getProcessor(zPlane), selectedRoiColor(), overlay);
        row.rawImp.setOverlay(overlay);
        row.rawImp.updateAndDraw();
        row.lastRenderedZ = zPlane;

        ImagePlus zProj = row.getZProjImage();
        if (zProj != null) {
            if (beforeZProj != null) beforeZProj.run();
            renderRoiOverlayOnZProj(zProj, row, seedSeg, finalSeg, areaEn, progress);
        }
    }

    private void renderOverlayOnZProj(ImagePlus zProj, SegmentationResult3D seedSeg, SegmentationResult3D finalSeg, boolean areaEn) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        int w = finalSeg.labelImage.getWidth();
        int h = finalSeg.labelImage.getHeight();
        int d = finalSeg.labelImage.getNSlices();
        int[] typeMap = new int[w * h];
        boolean hasSeed = areaEn && seedSeg != null && seedSeg.labelImage != null;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int type = 0;
                for (int z = 1; z <= d; z++) {
                    if (hasSeed && z <= seedSeg.labelImage.getNSlices()
                        && (int) Math.round(seedSeg.labelImage.getStack().getProcessor(z).getPixelValue(x, y)) > 0) {
                        type = 1;
                        break;
                    }
                    if ((int) Math.round(finalSeg.labelImage.getStack().getProcessor(z).getPixelValue(x, y)) > 0) {
                        type = 2;
                    }
                }
                typeMap[y * w + x] = type;
            }
        }
        int seedRgb = toRgbSolid(selectedSeedPreviewColor());
        int areaRgb = toRgbSolid(selectedRoiColor());
        ColorProcessor cp = new ColorProcessor(w, h);
        int[] pixels = (int[]) cp.getPixels();
        for (int i = 0; i < typeMap.length; i++) {
            if (typeMap[i] == 1) pixels[i] = seedRgb;
            else if (typeMap[i] == 2) pixels[i] = areaRgb;
        }
        ImageRoi iroi = new ImageRoi(0, 0, cp);
        iroi.setZeroTransparent(true);
        iroi.setOpacity(getOverlayOpacity());
        Overlay overlay = new Overlay();
        overlay.add(iroi);
        zProj.setOverlay(overlay);
        zProj.updateAndDraw();
    }

    private void renderRoiOverlayOnZProj(ImagePlus zProj, TargetRow row, SegmentationResult3D seedSeg,
                                         SegmentationResult3D finalSeg, boolean areaEn) {
        renderRoiOverlayOnZProj(zProj, row, seedSeg, finalSeg, areaEn, null);
    }

    private void renderRoiOverlayOnZProj(ImagePlus zProj, TargetRow row, SegmentationResult3D seedSeg,
                                         SegmentationResult3D finalSeg, boolean areaEn,
                                         java.util.function.Consumer<String> progress) {
        if (finalSeg == null || finalSeg.labelImage == null) return;
        if (row.cachedZProjAreaRois == null) {
            row.cachedZProjAreaRois = SeededSpotQuantifier3DImageSupport.buildLabelUnionRois(finalSeg.labelImage, "result", progress);
            row.cachedZProjSeedRois = (areaEn && seedSeg != null && seedSeg.labelImage != null)
                ? SeededSpotQuantifier3DImageSupport.buildLabelUnionRois(seedSeg.labelImage, "seed", progress) : null;
        }
        Overlay overlay = new Overlay();
        if (row.cachedZProjSeedRois != null) {
            for (Roi roi : row.cachedZProjSeedRois) {
                Roi copy = (Roi) roi.clone();
                copy.setStrokeColor(selectedSeedPreviewColor());
                overlay.add(copy);
            }
        }
        for (Roi roi : row.cachedZProjAreaRois) {
            Roi copy = (Roi) roi.clone();
            copy.setStrokeColor(selectedRoiColor());
            overlay.add(copy);
        }
        zProj.setOverlay(overlay);
        zProj.updateAndDraw();
    }

    private static void addLabelOutlines(ImageProcessor labelIp, Color color, Overlay overlay) {
        int w = labelIp.getWidth();
        int h = labelIp.getHeight();
        HashMap<Integer, int[]> bboxMap = new HashMap<Integer, int[]>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (int) Math.round(labelIp.getPixelValue(x, y));
                if (v <= 0) continue;
                int[] bb = bboxMap.get(v);
                if (bb == null) bboxMap.put(v, new int[]{x, y, x, y});
                else {
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
            int x0 = Math.max(0, bb[0] - 1);
            int y0 = Math.max(0, bb[1] - 1);
            int x1 = Math.min(w - 1, bb[2] + 1);
            int y1 = Math.min(h - 1, bb[3] + 1);
            int bw = x1 - x0 + 1;
            int bh = y1 - y0 + 1;
            ByteProcessor bp = new ByteProcessor(bw, bh);
            byte[] pixels = (byte[]) bp.getPixels();
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if ((int) Math.round(labelIp.getPixelValue(x, y)) == label) {
                        pixels[(y - y0) * bw + (x - x0)] = (byte) 255;
                    }
                }
            }
            bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roi = ThresholdToSelection.run(new ImagePlus("", bp));
            if (roi == null) continue;
            roi.setLocation(roi.getXBase() + x0, roi.getYBase() + y0);
            roi.setStrokeColor(color);
            overlay.add(roi);
        }
    }

    private void clearAllOverlays() {
        for (TargetRow row : targetRows) clearOverlay(row);
    }

    private void clearOverlay(TargetRow row) {
        row.rawImp.setOverlay((Overlay) null);
        row.rawImp.updateAndDraw();
        ImagePlus zProj = row.getZProjImage();
        if (zProj != null) {
            zProj.setOverlay((Overlay) null);
            zProj.updateAndDraw();
        }
    }

    private int currentZPlane(ImagePlus rawImp) {
        if (rawImp == null) return 1;
        int z = rawImp.getZ();
        return z > 0 ? z : rawImp.getCurrentSlice();
    }

    private QuantifierParams buildParams() {
        Double minVol = minVolEnabled ? minVolVal : null;
        Double maxVol = maxVolEnabled ? maxVolVal : null;
        int conn = Integer.parseInt(connectivityChoice.getSelectedItem());
        return new QuantifierParams(areaThreshold, minVol, maxVol, false, 1.0, 0.5, conn, fillHolesCheck.getState());
    }

    private void commitAreaThreshField() {
        if (syncing) return;
        int prevBg = areaThreshold;
        areaThreshold = Math.max(parseIntOr(areaThreshField.getText(), areaThreshold), 0);
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
        seedThreshold = Math.max(parseIntOr(seedThreshField.getText(), seedThreshold), 0);
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
        minVolVal = Math.max(0, parseDoubleOr(minVolField.getText(), minVolVal));
        syncing = true;
        minVolField.setText(formatVol(minVolVal));
        minVolBar.setValue(volToSlider(minVolVal));
        syncing = false;
        onParamsChanged();
    }

    private void commitMaxVolField() {
        if (syncing) return;
        maxVolVal = Math.max(0, parseDoubleOr(maxVolField.getText(), maxVolVal));
        syncing = true;
        maxVolField.setText(formatVol(maxVolVal));
        maxVolBar.setValue(volToSlider(maxVolVal));
        syncing = false;
        onParamsChanged();
    }

    private Color selectedSeedPreviewColor() {
        return Color.decode(SeededSpotQuantifier3DFrame.PREVIEW_COLOR_OPTIONS[seedColorChoice.getSelectedIndex()][1]);
    }

    private Color selectedRoiColor() {
        return Color.decode(SeededSpotQuantifier3DFrame.PREVIEW_COLOR_OPTIONS[roiColorChoice.getSelectedIndex()][1]);
    }

    private double getOverlayOpacity() {
        try {
            int v = Integer.parseInt(overlayOpacityField.getText().trim());
            return Math.max(0, Math.min(100, v)) / 100.0;
        } catch (NumberFormatException ex) {
            return 0.5;
        }
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
        return new Scrollbar(Scrollbar.HORIZONTAL, volToSlider(initVal), 1, 0, VOL_SLIDER_STEPS + 1);
    }

    private static String formatVol(double v) {
        if (v >= 10) return String.format("%.1f", v);
        if (v >= 1) return String.format("%.2f", v);
        return String.format("%.4f", v);
    }

    private static int parseIntOr(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private static double parseDoubleOr(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception ex) { return fallback; }
    }

    private static int safeImgMax(int imgMin, int imgMax) {
        return imgMax <= imgMin ? imgMin + 1 : imgMax;
    }

    private double computeVoxelVol(ImagePlus imp) {
        double pw = imp.getCalibration().pixelWidth > 0 ? imp.getCalibration().pixelWidth : 1;
        double ph = imp.getCalibration().pixelHeight > 0 ? imp.getCalibration().pixelHeight : 1;
        double pd = imp.getCalibration().pixelDepth > 0 ? imp.getCalibration().pixelDepth : 1;
        return pw * ph * pd;
    }

    private static int toRgbSolid(Color c) {
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private List<TargetRow> selectedTargets() {
        List<TargetRow> out = new ArrayList<TargetRow>();
        for (TargetRow row : targetRows) if (row.useCheck.isSelected()) out.add(row);
        return out;
    }

    private TargetRow firstSelectedTarget() {
        for (TargetRow row : targetRows) if (row.useCheck.isSelected()) return row;
        return null;
    }

    private boolean hasPreviewResults() {
        for (TargetRow row : targetRows) if (row.previewResult != null) return true;
        return false;
    }

    private void startZWatch() {
        zWatchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (previewOff.getState()) return;
                EventQueue.invokeLater(() -> {
                    for (TargetRow row : targetRows) {
                        if (row.previewResult == null) continue;
                        if (currentZPlane(row.rawImp) != row.lastRenderedZ) renderPreviewForRow(row, row.previewResult);
                    }
                });
            }
        }, 300, 300);
    }

    private void setStatusText(String text) {
        statusLabel.setText(text);
        IJ.showStatus(text == null || text.isEmpty() ? "" : "Seeded Spot Quantifier 3D Multi: " + text);
    }

    private void placeNearActiveWindow() {
        Window active = representativeRawImp != null ? representativeRawImp.getWindow() : IJ.getInstance();
        if (active == null) return;
        Point p;
        try { p = active.getLocationOnScreen(); } catch (IllegalComponentStateException ex) { return; }
        int x = p.x + active.getWidth() + 8;
        int y = p.y;
        Rectangle screen = active.getGraphicsConfiguration() != null
            ? active.getGraphicsConfiguration().getBounds()
            : new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
        if (x + getWidth() > screen.x + screen.width) x = Math.max(screen.x, p.x - getWidth() - 8);
        if (y + getHeight() > screen.y + screen.height) y = Math.max(screen.y, screen.y + screen.height - getHeight());
        setLocation(x, y);
    }

    private static final class PreviewSummary {
        int updatedCount;
        int emptyCount;
        int failedCount;
        int channelMismatchCount;
        int zProjUpdatedCount;
        final List<String> failedImages = new ArrayList<String>();

        String toStatus() {
            return updatedCount + " images updated, "
                + emptyCount + " empty, "
                + failedCount + " failed, "
                + channelMismatchCount + " channel-mismatch, "
                + zProjUpdatedCount + " z-proj updated";
        }
    }

    private static final class SaveSummary {
        final int total;
        int ok;
        int failed;
        final List<String> failedImages = new ArrayList<String>();

        SaveSummary(int total) {
            this.total = total;
        }

        String toStatus() {
            return "Saved " + ok + "/" + total + " images, " + failed + " failed";
        }
    }

    private final class TargetRow {
        final ImagePlus rawImp;
        final JCheckBox useCheck = new JCheckBox();
        final JLabel titleLabel;
        final JComboBox<String> zProjChoice = new JComboBox<String>();
        SeededQuantifier3D.SeededResult previewResult;
        List<Roi> cachedZProjSeedRois;
        List<Roi> cachedZProjAreaRois;
        int lastRenderedZ = -1;

        TargetRow(ImagePlus rawImp) {
            this.rawImp = rawImp;
            this.titleLabel = new JLabel(rawImp.getTitle());
            this.zProjChoice.setPreferredSize(new Dimension(130, 24));
        }

        ImagePlus getZProjImage() {
            Object item = zProjChoice.getSelectedItem();
            return SeededSpotQuantifier3DImageSupport.findImageByTitle(item == null ? null : item.toString());
        }

        String getSelectedZProjTitle() {
            Object item = zProjChoice.getSelectedItem();
            return item == null ? NONE_ITEM : item.toString();
        }

        void clearZProjCache() {
            cachedZProjSeedRois = null;
            cachedZProjAreaRois = null;
        }

        void clearPreviewResult() {
            previewResult = null;
            clearZProjCache();
            lastRenderedZ = -1;
        }
    }
}
