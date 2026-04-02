package jp.yourorg.fiji_maxima_based_segmenter.ui;

import ij.ImagePlus;
import jp.yourorg.fiji_maxima_based_segmenter.core.ThresholdModel;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HistogramPanel extends Panel {
    private ImagePlus imp;
    private final ThresholdModel model;
    private final ThresholdListener listener;
    private int[] histogram;
    private int histMax = 1;
    private int histSlice = -1;
    private int dragMode = 0; // 0 none, 1 bg, 2 fg
    private boolean fgEnabled = true;
    private boolean logScale = true;
    private static final int PAD = 8;
    private static final int HANDLE_TOL = 4;

    public interface ThresholdListener {
        void onThresholdsChanged(int tBg, int tFg);
    }

    public HistogramPanel(ImagePlus imp, ThresholdModel model, ThresholdListener listener) {
        this.imp = imp;
        this.model = model;
        this.listener = listener;
        setPreferredSize(new Dimension(420, 140));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int bgX = valueToX(model.getTBg());
                int fgX = valueToX(model.getTFg());
                if (Math.abs(x - bgX) <= HANDLE_TOL) dragMode = 1;
                else if (fgEnabled && Math.abs(x - fgX) <= HANDLE_TOL) dragMode = 2;
                else {
                    dragMode = 0;
                    int value = xToValue(x);
                    int curBg = model.getTBg();
                    int curFg = model.getTFg();
                    if (Math.abs(value - curBg) <= Math.abs(value - curFg)) {
                        listener.onThresholdsChanged(value, curFg);
                    } else if (fgEnabled) {
                        listener.onThresholdsChanged(curBg, value);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragMode = 0;
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragMode == 0) return;
                int value = xToValue(e.getX());
                if (dragMode == 1) listener.onThresholdsChanged(value, model.getTFg());
                else listener.onThresholdsChanged(model.getTBg(), value);
            }
        });
    }

    public void setImage(ImagePlus imp) {
        this.imp = imp;
        this.histSlice = -1;
        repaint();
    }

    public void setFgEnabled(boolean enabled) {
        this.fgEnabled = enabled;
        repaint();
    }

    public void setLogScale(boolean log) {
        this.logScale = log;
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        if (imp == null) return;
        ensureHistogram();
        int w = getWidth();
        int h = getHeight();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawRect(PAD, PAD, w - 2 * PAD, h - 2 * PAD);

        int plotW = w - 2 * PAD;
        int plotH = h - 2 * PAD;
        int bins = histogram.length;
        g.setColor(Color.GRAY);
        double scaleMax = logScale ? Math.log1p(histMax) : histMax;
        for (int i = 0; i < bins; i++) {
            int x = PAD + (int) Math.round(i * (plotW - 1) / (double) (bins - 1));
            double val = logScale ? Math.log1p(histogram[i]) : histogram[i];
            int barH = scaleMax > 0 ? (int) Math.round(val * (plotH - 2) / scaleMax) : 0;
            g.drawLine(x, PAD + plotH - 1, x, PAD + plotH - 1 - barH);
        }

        int bgX = valueToX(model.getTBg());
        int fgX = valueToX(model.getTFg());
        g.setColor(Color.BLACK);
        g.drawLine(bgX, PAD, bgX, PAD + plotH);
        if (fgEnabled) {
            g.drawLine(fgX, PAD, fgX, PAD + plotH);
        } else {
            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(fgX, PAD, fgX, PAD + plotH);
            g.drawString("T_fg unused", fgX + 4, PAD + 12);
        }
    }

    private void ensureHistogram() {
        if (imp == null) return;
        // For 3D stacks, compute histogram once for entire stack (not per-slice)
        boolean is3D = imp.getNSlices() > 1;
        int slice = is3D ? -1 : imp.getCurrentSlice(); // -1 means "all slices"
        if (histogram != null && histSlice == slice) return;
        histogram = computeHistogram(imp, model.getMinValue(), model.getMaxValue(), is3D);
        histMax = 1;
        for (int v : histogram) if (v > histMax) histMax = v;
        histSlice = slice;
    }

    private int[] computeHistogram(ImagePlus imp, int minValue, int maxValue, boolean allSlices) {
        int bins = 256;
        int[] hist = new int[bins];
        if (maxValue <= minValue) return hist;
        int w = imp.getWidth();
        int h = imp.getHeight();
        
        if (allSlices && imp.getNSlices() > 1) {
            // Compute histogram for entire 3D stack
            int nSlices = imp.getNSlices();
            for (int z = 1; z <= nSlices; z++) {
                imp.setSliceWithoutUpdate(z);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        double v = imp.getProcessor().getPixelValue(x, y);
                        int bin = (int) Math.round((v - minValue) / (maxValue - minValue) * (bins - 1));
                        if (bin < 0) bin = 0;
                        if (bin >= bins) bin = bins - 1;
                        hist[bin]++;
                    }
                }
            }
        } else {
            // Compute histogram for current slice only (2D)
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double v = imp.getProcessor().getPixelValue(x, y);
                    int bin = (int) Math.round((v - minValue) / (maxValue - minValue) * (bins - 1));
                    if (bin < 0) bin = 0;
                    if (bin >= bins) bin = bins - 1;
                    hist[bin]++;
                }
            }
        }
        return hist;
    }

    private int valueToX(int value) {
        int w = getWidth() - 2 * PAD;
        int min = model.getMinValue();
        int max = model.getMaxValue();
        if (max <= min) return PAD;
        double t = (value - min) / (double) (max - min);
        return PAD + (int) Math.round(t * (w - 1));
    }

    private int xToValue(int x) {
        int w = getWidth() - 2 * PAD;
        int min = model.getMinValue();
        int max = model.getMaxValue();
        int clamped = Math.max(PAD, Math.min(PAD + w - 1, x));
        double t = (clamped - PAD) / (double) (w - 1);
        if (max <= min) return min;
        return (int) Math.round(min + t * (max - min));
    }
}
