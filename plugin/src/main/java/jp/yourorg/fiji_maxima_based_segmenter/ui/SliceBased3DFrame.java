package jp.yourorg.fiji_maxima_based_segmenter.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.frame.PlugInFrame;
import jp.yourorg.fiji_maxima_based_segmenter.core.*;
import jp.yourorg.fiji_maxima_based_segmenter.preview.PreviewRenderer;
import jp.yourorg.fiji_maxima_based_segmenter.alg.SegmentationResult;
import jp.yourorg.fiji_maxima_based_segmenter.alg.WatershedRunner;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/**
 * Simplified GUI frame for the Slice-Based 3D Segmenter.
 * Follows the same pattern as SimpleSegmenterFrame — only bg threshold and tolerance.
 */
public class SliceBased3DFrame extends PlugInFrame {
    private ImagePlus imp;
    private final ThresholdModel model;

    private final Scrollbar bgBar;
    private final TextField bgField;

    private final Scrollbar toleranceBar;
    private final TextField toleranceField;

    private final CheckboxGroup previewGroup = new CheckboxGroup();
    private final Checkbox previewOff;
    private final Checkbox previewMarker;
    private final Checkbox previewRoi;

    private final Checkbox logScaleCb;

    private final Button applyBtn = new Button("Apply");
    private final Button addRoiBtn = new Button("Add ROI");
    private final Button saveRoiBtn = new Button("Save ROI");

    private boolean syncing = false;
    private final PreviewRenderer previewRenderer = new PreviewRenderer();
    private final HistogramPanel histogramPanel;
    private final Timer previewTimer = new Timer("slice-3d-preview", true);
    private TimerTask previewTask;
    private final AtomicInteger previewGen = new AtomicInteger();
    private int lastZPlane = -1;
    private final Timer zWatchTimer = new Timer("slice-3d-z-watch", true);

    public SliceBased3DFrame(ImagePlus imp) {
        super("Slice_Based_3D_Segmenter");
        this.imp = imp;
        this.model = ThresholdModel.createForSimplePlugin(imp);

        int min = model.getMinValue();
        int max = model.getMaxValue();
        if (max <= min) max = min + 1;

        bgBar = new Scrollbar(Scrollbar.HORIZONTAL, model.getTBg(), 1, min, max + 1);
        bgField = new TextField(Integer.toString(model.getTBg()), 5);

        toleranceBar = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, 101);
        toleranceField = new TextField(Double.toString(model.getFindMaximaTolerance()), 6);

        previewOff = new Checkbox("Off", previewGroup, model.getPreviewMode() == PreviewMode.OFF);
        previewMarker = new Checkbox("Seed preview", previewGroup, model.getPreviewMode() == PreviewMode.MARKER_BOUNDARIES);
        previewRoi = new Checkbox("ROI boundaries", previewGroup, model.getPreviewMode() == PreviewMode.ROI_BOUNDARIES);

        logScaleCb = new Checkbox("Log scale", false);

        histogramPanel = new HistogramPanel(imp, model, this::onHistogramThreshold);
        histogramPanel.setFgEnabled(false);

        buildLayout();
        wireEvents();
        syncAllFromModel();
        startZWatch();
        pack();
        placeNearImage();
    }

    private void placeNearImage() {
        Window active = imp != null ? imp.getWindow() : null;
        if (active == null) return;
        Point p;
        try {
            p = active.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }
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

    private void buildLayout() {
        setLayout(new BorderLayout());

        // Top: histogram + log scale checkbox
        Panel top = new Panel(new BorderLayout());
        top.add(histogramPanel, BorderLayout.CENTER);
        Panel histOptions = new Panel(new FlowLayout(FlowLayout.RIGHT));
        histOptions.add(logScaleCb);
        top.add(histOptions, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        Panel center = new Panel(new GridLayout(0, 1));

        // BG threshold row
        Panel bgRow = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        bgRow.add(new Label("Background:"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        bgBar.setPreferredSize(new Dimension(260, 18));
        bgRow.add(bgBar, c);
        c.gridx = 2;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        bgRow.add(bgField, c);

        // Tolerance row
        Panel tolRow = new Panel(new GridBagLayout());
        GridBagConstraints tc = new GridBagConstraints();
        tc.gridy = 0;
        tc.insets = new Insets(2, 4, 2, 4);
        tc.anchor = GridBagConstraints.WEST;
        tc.gridx = 0;
        tolRow.add(new Label("Tolerance:"), tc);
        tc.gridx = 1;
        tc.weightx = 1.0;
        tc.fill = GridBagConstraints.HORIZONTAL;
        toleranceBar.setPreferredSize(new Dimension(260, 18));
        tolRow.add(toleranceBar, tc);
        tc.gridx = 2;
        tc.weightx = 0.0;
        tc.fill = GridBagConstraints.NONE;
        tolRow.add(toleranceField, tc);

        // Preview row
        Panel previewPanel = new Panel(new FlowLayout(FlowLayout.LEFT));
        previewPanel.add(new Label("Preview:"));
        previewPanel.add(previewOff);
        previewPanel.add(previewMarker);
        previewPanel.add(previewRoi);

        center.add(bgRow);
        center.add(tolRow);
        center.add(previewPanel);
        add(center, BorderLayout.CENTER);

        // Buttons
        Panel buttons = new Panel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(applyBtn);
        buttons.add(addRoiBtn);
        buttons.add(saveRoiBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private void wireEvents() {
        bgBar.addAdjustmentListener(e -> {
            if (syncing) return;
            model.setTBg(bgBar.getValue());
            syncAllFromModel();
            onStateChanged();
        });
        bgField.addActionListener(e -> commitBgField());
        bgField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitBgField(); }
        });

        toleranceBar.addAdjustmentListener(e -> {
            if (syncing) return;
            double v = toleranceFromSlider(toleranceBar.getValue());
            model.setFindMaximaTolerance(v);
            toleranceField.setText(Double.toString(v));
            onStateChanged();
        });
        toleranceField.addActionListener(e -> commitToleranceField());
        toleranceField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { commitToleranceField(); }
        });

        ItemListener previewListener = e -> {
            if (syncing) return;
            if (previewGroup.getSelectedCheckbox() == previewOff) model.setPreviewMode(PreviewMode.OFF);
            else if (previewGroup.getSelectedCheckbox() == previewMarker) model.setPreviewMode(PreviewMode.MARKER_BOUNDARIES);
            else model.setPreviewMode(PreviewMode.ROI_BOUNDARIES);
            onStateChanged();
        };
        previewOff.addItemListener(previewListener);
        previewMarker.addItemListener(previewListener);
        previewRoi.addItemListener(previewListener);

        logScaleCb.addItemListener(e -> {
            histogramPanel.setLogScale(logScaleCb.getState());
        });

        applyBtn.addActionListener(e -> runApply());
        addRoiBtn.addActionListener(e -> runApply()); // Add ROI = Apply for 3D
        saveRoiBtn.addActionListener(e -> runSaveRoi());

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                zWatchTimer.cancel();
                clearOverlay();
                dispose();
            }
        });
    }

    private void onStateChanged() {
        if (model.getPreviewMode() == PreviewMode.OFF) {
            clearOverlay();
            cancelPreview();
            return;
        }
        schedulePreview(model.getPreviewMode());
    }

    private void startZWatch() {
        lastZPlane = imp.getCurrentSlice();
        zWatchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (imp == null) return;
                int z = imp.getCurrentSlice();
                if (z != lastZPlane) {
                    lastZPlane = z;
                    EventQueue.invokeLater(() -> {
                        if (model.getPreviewMode() != PreviewMode.OFF) {
                            schedulePreview(model.getPreviewMode());
                        }
                    });
                }
            }
        }, 300, 300);
    }

    private void runApply() {
        applyBtn.setEnabled(false);
        addRoiBtn.setEnabled(false);
        applyBtn.setLabel("Processing...");
        IJ.showStatus("Starting 3D segmentation...");

        Thread segThread = new Thread(() -> {
            try {
                ImagePlus result = jp.yourorg.fiji_maxima_based_segmenter.Slice_Based_3D_Segmenter_.segment(
                    imp, model.getTBg(), model.getFindMaximaTolerance()
                );

                SwingUtilities.invokeLater(() -> {
                    if (result != null) {
                        IJ.showStatus("3D segmentation completed successfully");
                    } else {
                        IJ.error("Slice-Based 3D Segmenter",
                            "Segmentation failed. Check the Log window for details.");
                    }
                    applyBtn.setEnabled(true);
                    addRoiBtn.setEnabled(true);
                    applyBtn.setLabel("Apply");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    IJ.error("Slice-Based 3D Segmenter", "Error: " + e.getMessage());
                    applyBtn.setEnabled(true);
                    addRoiBtn.setEnabled(true);
                    applyBtn.setLabel("Apply");
                });
            }
        }, "Slice-Based-3D-Segmentation");
        segThread.start();
    }

    private void runSaveRoi() {
        ij.plugin.frame.RoiManager rm = ij.plugin.frame.RoiManager.getInstance();
        if (rm == null || rm.getCount() == 0) {
            IJ.error("Save ROI", "No ROIs in ROI Manager. Run Apply first.");
            return;
        }
        java.awt.FileDialog fd = new java.awt.FileDialog(this, "Save ROIs as ZIP", java.awt.FileDialog.SAVE);
        fd.setFile("RoiSet.zip");
        fd.setVisible(true);
        String dir = fd.getDirectory();
        String name = fd.getFile();
        if (dir == null || name == null) return;
        RoiExporter.saveRoiManagerToZip(dir + name);
    }

    // --- Preview ---

    private void schedulePreview(PreviewMode mode) {
        if (mode == PreviewMode.OFF) return;
        int gen = previewGen.incrementAndGet();
        cancelPreviewTaskOnly();
        previewTask = new TimerTask() {
            @Override
            public void run() {
                if (previewGen.get() != gen) return;
                SwingUtilities.invokeLater(() -> runPreview(mode));
            }
        };
        int delay = Math.max(50, model.getPreviewDebounceMs());
        previewTimer.schedule(previewTask, delay);
    }

    private void cancelPreview() {
        previewGen.incrementAndGet();
        cancelPreviewTaskOnly();
    }

    private void cancelPreviewTaskOnly() {
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
    }

    private void runPreview(PreviewMode mode) {
        if (imp == null || mode == PreviewMode.OFF) {
            clearOverlay();
            return;
        }
        try {
            int currentSlice = imp.getCurrentSlice();
            ImageProcessor sliceProc = imp.getStack().getProcessor(currentSlice);
            ImagePlus slice = new ImagePlus(imp.getShortTitle() + "_slice_" + currentSlice, sliceProc);

            ThresholdModel sliceModel = ThresholdModel.createForSimplePlugin(slice);
            sliceModel.setTBg(model.getTBg());
            sliceModel.setFindMaximaTolerance(model.getFindMaximaTolerance());

            MarkerBuilder markerBuilder = new MarkerBuilder();
            MarkerResult markers = markerBuilder.build(slice, sliceModel);

            if (mode == PreviewMode.MARKER_BOUNDARIES) {
                previewRenderer.renderMarkerFill(imp, markers, sliceModel.getAppearance(), sliceModel.getMarkerSource());
            } else {
                if (markers.fgCount == 0) {
                    clearOverlay();
                    return;
                }
                WatershedRunner ws = new WatershedRunner();
                SegmentationResult seg = ws.run(
                    slice, markers, sliceModel.getSurface(), sliceModel.getConnectivity(), false, 0.0);
                previewRenderer.renderSegmentationBoundaries(imp, seg);
            }
        } catch (Exception e) {
            IJ.log("Preview error: " + e.getMessage());
            clearOverlay();
        }
    }

    // --- Sync ---

    private void syncAllFromModel() {
        syncing = true;
        try {
            int min = model.getMinValue();
            int max = model.getMaxValue();
            if (max <= min) max = min + 1;
            bgBar.setMinimum(min);
            bgBar.setMaximum(max + 1);
            bgBar.setValue(model.getTBg());
            bgField.setText(Integer.toString(model.getTBg()));
            updateToleranceRange();
            toleranceBar.setValue(toleranceToSlider(model.getFindMaximaTolerance()));
            toleranceField.setText(Double.toString(model.getFindMaximaTolerance()));
            previewOff.setState(model.getPreviewMode() == PreviewMode.OFF);
            previewMarker.setState(model.getPreviewMode() == PreviewMode.MARKER_BOUNDARIES);
            previewRoi.setState(model.getPreviewMode() == PreviewMode.ROI_BOUNDARIES);
            histogramPanel.repaint();
        } finally {
            syncing = false;
        }
    }

    // --- Field commits ---

    private void commitBgField() {
        if (syncing) return;
        model.setTBg(parseOrKeep(bgField.getText(), model.getTBg()));
        syncAllFromModel();
        onStateChanged();
    }

    private void commitToleranceField() {
        if (syncing) return;
        double v = parseOrKeepDouble(toleranceField.getText(), model.getFindMaximaTolerance());
        v = Math.max(0, v);
        model.setFindMaximaTolerance(v);
        toleranceField.setText(Double.toString(v));
        toleranceBar.setValue(toleranceToSlider(v));
        onStateChanged();
    }

    private void onHistogramThreshold(int tBg, int tFg) {
        if (syncing) return;
        model.setTBg(tBg);
        syncAllFromModel();
        onStateChanged();
    }

    // --- Tolerance slider ---

    private void updateToleranceRange() {
        int range = model.getMaxValue() - model.getMinValue();
        if (range < 1) range = 1;
        toleranceBar.setMinimum(0);
        toleranceBar.setMaximum(range + 1);
    }

    private int toleranceToSlider(double v) {
        return (int) Math.round(v);
    }

    private double toleranceFromSlider(int v) {
        return v;
    }

    // --- Util ---

    private int parseOrKeep(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception ex) { return fallback; }
    }

    private double parseOrKeepDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception ex) { return fallback; }
    }

    private void clearOverlay() {
        imp.setOverlay((Overlay) null);
        imp.updateAndDraw();
        imp.setRoi((Roi) null);
        Toolbar.getInstance().setTool(Toolbar.RECTANGLE);
    }

    public ThresholdModel getModel() {
        return model;
    }
}
