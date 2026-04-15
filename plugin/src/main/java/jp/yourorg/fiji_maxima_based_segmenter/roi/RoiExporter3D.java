package jp.yourorg.fiji_maxima_based_segmenter.roi;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.TreeSet;

public class RoiExporter3D {

    /**
     * Named ROI colors available in the UI.
     * Each entry is { display name, hex color code }.
     */
    public static final String[][] ROI_COLOR_OPTIONS = {
        { "Yellow",  "#FFFF00" },
        { "Cyan",    "#00FFFF" },
        { "Magenta", "#FF00FF" },
        { "Red",     "#FF0000" },
        { "Green",   "#00FF00" },
        { "White",   "#FFFFFF" },
    };

    /** Default ROI color (yellow). */
    public static final Color DEFAULT_ROI_COLOR = Color.decode(ROI_COLOR_OPTIONS[0][1]);

    /**
     * Decode a hex color string (e.g. "#FFFF00") to a Color.
     */
    public static Color decodeColor(String hex) {
        return Color.decode(hex);
    }

    /**
     * Export 3D segmentation as 2D ROI slices with Position and Group attributes.
     * Uses the default ROI color (yellow).
     */
    public void exportToRoiManager(ImagePlus labelImage) {
        exportToRoiManager(labelImage, DEFAULT_ROI_COLOR);
    }

    /**
     * Export 3D segmentation as 2D ROI slices with Position and Group attributes.
     *
     * @param roiColor stroke color applied to every exported ROI
     */
    public void exportToRoiManager(ImagePlus labelImage, Color roiColor) {
        if (labelImage == null) {
            IJ.error("Add ROI failed", "Label image is missing.");
            return;
        }
        ImageStack stack = labelImage.getStack();
        int w = labelImage.getWidth();
        int h = labelImage.getHeight();
        int d = labelImage.getNSlices();

        // Find all unique labels (use getPixel to handle float/int properly)
        TreeSet<Integer> labels = new TreeSet<>();
        for (int z = 1; z <= d; z++) {
            ImageProcessor ip = stack.getProcessor(z);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int v = (int) Math.round(ip.getPixelValue(x, y));
                    if (v > 0) labels.add(v);
                }
            }
        }

        if (labels.isEmpty()) {
            IJ.log("RoiExporter3D: no objects found in label image (skipping ROI export).");
            return;
        }

        RoiManager rm = RoiManager.getRoiManager();
        for (int label : labels) {
            for (int z = 1; z <= d; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                ByteProcessor bp = new ByteProcessor(w, h);
                byte[] pixels = (byte[]) bp.getPixels();
                boolean hasPixel = false;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int idx = y * w + x;
                        int v = (int) Math.round(ip.getPixelValue(x, y));
                        if (v == label) {
                            pixels[idx] = (byte) 255;
                            hasPixel = true;
                        }
                    }
                }
                if (!hasPixel) continue;

                bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
                ImagePlus mask = new ImagePlus("mask", bp);
                Roi roi = ThresholdToSelection.run(mask);
                if (roi == null) continue;

                roi.setPosition(z);
                roi.setStrokeColor(roiColor);
                roi.setName(String.format("obj-%03d-z%03d", label, z));
                rm.addRoi(roi);
            }
        }
    }
}
