package jp.yourorg.fiji_maxima_based_segmenter.util;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.Duplicator;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.alg.SegmentationResult3D;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class SeededSpotQuantifier3DImageSupport {
    public static final String NONE_ITEM = "None";

    private SeededSpotQuantifier3DImageSupport() {}

    public static ImagePlus extractProcessingImage(ImagePlus image, int channel) {
        int nCh = Math.max(1, image.getNChannels());
        if (nCh <= 1) return image;
        int ch = Math.max(1, Math.min(nCh, channel));
        return new Duplicator().run(image, ch, ch, 1, image.getNSlices(), 1, image.getNFrames());
    }

    public static void disposeProcessingImage(ImagePlus image, boolean owned) {
        if (image == null || !owned) return;
        image.flush();
    }

    public static int[] computeStackMinMax(ImagePlus image) {
        if (image == null || image.getStackSize() <= 0) return new int[]{0, 1};
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 1; i <= image.getStackSize(); i++) {
            ImageProcessor ip = image.getStack().getProcessor(i);
            int w = ip.getWidth();
            int h = ip.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int v = (int) Math.round(ip.getPixelValue(x, y));
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
        }
        if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) return new int[]{0, 1};
        return new int[]{min, max};
    }

    public static List<ImagePlus> listOpen3DImages() {
        List<ImagePlus> out = new ArrayList<>();
        int[] ids = WindowManager.getIDList();
        if (ids == null) return out;
        for (int id : ids) {
            ImagePlus img = WindowManager.getImage(id);
            if (img != null && img.getNSlices() >= 2) out.add(img);
        }
        return out;
    }

    public static List<ImagePlus> listOpen2DImages() {
        List<ImagePlus> out = new ArrayList<>();
        int[] ids = WindowManager.getIDList();
        if (ids == null) return out;
        for (int id : ids) {
            ImagePlus img = WindowManager.getImage(id);
            if (img != null && img.getNSlices() < 2) out.add(img);
        }
        return out;
    }

    public static ImagePlus findImageByTitle(String title) {
        if (title == null || NONE_ITEM.equals(title)) return null;
        return WindowManager.getImage(title);
    }

    public static String autoMatchZProjTitle(ImagePlus rawImp, List<ImagePlus> zProjCandidates) {
        if (rawImp == null) return NONE_ITEM;
        String rawTitle = rawImp.getTitle();
        String match = null;
        for (ImagePlus candidate : zProjCandidates) {
            if (candidate == null) continue;
            if (!candidate.getTitle().contains(rawTitle)) continue;
            if (match != null) return NONE_ITEM;
            match = candidate.getTitle();
        }
        return match != null ? match : NONE_ITEM;
    }

    public static int countLabels(SegmentationResult3D seg) {
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

    public static List<Roi> buildLabelUnionRois(ImagePlus labelImp) {
        int w = labelImp.getWidth();
        int h = labelImp.getHeight();
        int d = labelImp.getNSlices();
        TreeSet<Integer> labels = new TreeSet<>();
        for (int z = 1; z <= d; z++) {
            ImageProcessor ip = labelImp.getStack().getProcessor(z);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int v = (int) Math.round(ip.getPixelValue(x, y));
                    if (v > 0) labels.add(v);
                }
            }
        }

        List<Roi> rois = new ArrayList<>();
        for (int label : labels) {
            ByteProcessor bp = new ByteProcessor(w, h);
            byte[] bpix = (byte[]) bp.getPixels();
            for (int z = 1; z <= d; z++) {
                ImageProcessor ip = labelImp.getStack().getProcessor(z);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if ((int) Math.round(ip.getPixelValue(x, y)) == label) {
                            bpix[y * w + x] = (byte) 255;
                        }
                    }
                }
            }
            bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roi = ThresholdToSelection.run(new ImagePlus("", bp));
            if (roi != null) rois.add(roi);
        }
        return rois;
    }
}
