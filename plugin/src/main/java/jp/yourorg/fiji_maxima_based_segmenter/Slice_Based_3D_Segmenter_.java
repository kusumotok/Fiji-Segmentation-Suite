package jp.yourorg.fiji_maxima_based_segmenter;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.core.*;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter3D;
import jp.yourorg.fiji_maxima_based_segmenter.ui.SliceBased3DFrame;
import java.util.*;

/**
 * Slice-Based 3D Segmenter Plugin
 *
 * Performs 3D segmentation by applying 2D Simple segmentation (Watershed,
 * INVERT_ORIGINAL, C4) to each slice and merging overlapping regions.
 *
 * Parameters: bg_threshold, tolerance (same as Simple plugin).
 */
public class Slice_Based_3D_Segmenter_ implements PlugIn {

    private static boolean validateInput(ImagePlus imp) {
        if (imp == null) {
            IJ.error("Slice-Based 3D Segmenter", "No image provided.");
            return false;
        }
        if (imp.getNSlices() < 2) {
            IJ.error("Slice-Based 3D Segmenter",
                     "Image must be a 3D stack with at least 2 slices. " +
                     "Current image has " + imp.getNSlices() + " slice(s).");
            return false;
        }
        return true;
    }

    @Override
    public void run(String arg) {
        String macroOptions = Macro.getOptions();
        if (macroOptions != null && !macroOptions.trim().isEmpty()) {
            runMacroMode(macroOptions.trim());
            return;
        }

        ImagePlus imp = IJ.getImage();
        if (!validateInput(imp)) return;

        SliceBased3DFrame frame = new SliceBased3DFrame(imp);
        frame.setVisible(true);
    }

    private void runMacroMode(String options) {
        ImagePlus imp = IJ.getImage();
        if (!validateInput(imp)) return;

        // Parse only bg_threshold and tolerance
        int bgThreshold = 0;
        double tolerance = 10.0;

        String bgStr = Macro.getValue(options, "bg_threshold", null);
        if (bgStr != null) {
            try { bgThreshold = Math.max(0, Integer.parseInt(bgStr)); }
            catch (NumberFormatException e) { IJ.log("Warning: Invalid bg_threshold, using 0"); }
        }

        String tolStr = Macro.getValue(options, "tolerance", null);
        if (tolStr != null) {
            try { tolerance = Math.max(0, Double.parseDouble(tolStr)); }
            catch (NumberFormatException e) { IJ.log("Warning: Invalid tolerance, using 10.0"); }
        }

        ImagePlus result = performSegmentation(imp, bgThreshold, tolerance);
        if (result != null) {
            exportToRoiManager(result);
            IJ.log("Slice-Based 3D Segmenter: Segmentation completed successfully.");
        } else {
            IJ.error("Slice-Based 3D Segmenter", "Segmentation failed.");
        }
    }

    /**
     * Programmatic API for 3D slice-based segmentation.
     *
     * @param imp Input 3D image stack
     * @param bgThreshold Background threshold value
     * @param tolerance Extended Maxima tolerance value
     * @return 3D label image or null if segmentation fails
     */
    public static ImagePlus segment(ImagePlus imp, int bgThreshold, double tolerance) {
        if (!validateInput(imp)) return null;

        ImagePlus result = performSegmentation(imp, bgThreshold, tolerance);
        if (result != null) {
            exportToRoiManager(result);
        }
        return result;
    }

    private static boolean exportToRoiManager(ImagePlus labelImage) {
        if (labelImage == null) {
            IJ.log("Warning: Cannot export null label image to ROI Manager");
            return false;
        }

        try {
            RoiExporter3D exporter = new RoiExporter3D();
            exporter.exportToRoiManager(labelImage);

            Set<Integer> uniqueLabels = getUniqueLabels(labelImage);
            int regionCount = uniqueLabels.size();

            IJ.log("Slice-Based 3D Segmenter: Results exported to ROI Manager.");
            IJ.log(String.format("Exported %d 3D regions as ROIs to ROI Manager.", regionCount));
            IJ.showStatus("3D segmentation results available in ROI Manager");

            IJ.showMessage("Slice-Based 3D Segmenter - Export Complete",
                String.format(
                    "Segmentation completed successfully!\n\n" +
                    "%d 3D regions have been exported to ROI Manager\n\n" +
                    "To access your results:\n" +
                    "Open ROI Manager (Analyze > Tools > ROI Manager)",
                    regionCount));

            return true;
        } catch (Exception e) {
            IJ.error("ROI Export Error", "Failed to export results to ROI Manager: " + e.getMessage());
            return false;
        }
    }

    private static ImagePlus performSegmentation(ImagePlus imp, int bgThreshold, double tolerance) {
        try {
            long startTime = System.currentTimeMillis();
            IJ.log("Slice-Based 3D Segmenter: Starting segmentation of " + imp.getNSlices() + " slices...");

            // Step 1: Segment each slice
            IJ.log("Step 1: Processing individual slices with 2D segmentation...");
            SliceSegmenter sliceSegmenter = new SliceSegmenter();
            List<ImagePlus> sliceLabels = sliceSegmenter.segmentAllSlices(imp, bgThreshold, tolerance);

            // Count 2D regions
            int total2DRegions = 0;
            int slicesWithRegions = 0;
            for (int i = 0; i < sliceLabels.size(); i++) {
                ImagePlus slice = sliceLabels.get(i);
                if (slice != null) {
                    Set<Integer> labels = getUniqueLabels(slice);
                    int regionCount = labels.size();
                    total2DRegions += regionCount;
                    if (regionCount > 0) {
                        slicesWithRegions++;
                        IJ.log(String.format("  Slice %d: %d regions detected", i + 1, regionCount));
                    }
                }
            }

            IJ.log(String.format("2D segmentation complete: %d total regions across %d slices",
                                total2DRegions, slicesWithRegions));

            if (total2DRegions == 0) {
                IJ.log("Slice-Based 3D Segmenter: No regions detected in any slice.");
                IJ.showMessage("Warning",
                    "No regions detected. Try adjusting parameters (lower bg_threshold, higher tolerance).");
                return null;
            }

            // Step 2: Merge overlapping regions
            IJ.log("Step 2: Merging overlapping regions across slices...");
            SliceMerger sliceMerger = new SliceMerger();
            ImagePlus merged3D = sliceMerger.mergeSlices(sliceLabels);

            if (merged3D == null) {
                IJ.error("Slice-Based 3D Segmenter", "Failed to merge slices.");
                return null;
            }

            Set<Integer> unique3DLabels = getUniqueLabels(merged3D);
            IJ.log(String.format("Slice merging complete: %d 3D regions created", unique3DLabels.size()));

            // Step 3: Reassign labels
            IJ.log("Step 3: Reassigning labels to consecutive integers...");
            LabelReassigner labelReassigner = new LabelReassigner();
            ImagePlus finalResult = labelReassigner.reassignLabels(merged3D);

            if (finalResult == null) {
                IJ.error("Slice-Based 3D Segmenter", "Failed to reassign labels.");
                return null;
            }

            Set<Integer> finalLabels = getUniqueLabels(finalResult);

            // Statistics
            long processingTime = System.currentTimeMillis() - startTime;
            Map<Integer, Integer> voxelCounts = calculateVoxelCounts(finalResult);
            MergeStatistics stats = new MergeStatistics(
                    imp.getNSlices(), total2DRegions, finalLabels.size(),
                    processingTime, voxelCounts);
            displayStatisticsSummary(stats, imp);

            finalResult.setTitle("Slice-Based 3D Segmentation Result");
            return finalResult;

        } catch (OutOfMemoryError e) {
            IJ.error("Slice-Based 3D Segmenter",
                     "Out of memory. Try processing a smaller region or increasing Java heap size.");
            return null;
        } catch (Exception e) {
            IJ.error("Slice-Based 3D Segmenter",
                     "An error occurred during processing: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static Set<Integer> getUniqueLabels(ImagePlus image) {
        Set<Integer> uniqueLabels = new HashSet<>();
        if (image == null) return uniqueLabels;

        int nSlices = image.getNSlices();
        for (int z = 1; z <= nSlices; z++) {
            image.setSlice(z);
            ImageProcessor proc = image.getProcessor();
            int width = proc.getWidth();
            int height = proc.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int label = proc.getPixel(x, y);
                    if (label > 0) uniqueLabels.add(label);
                }
            }
        }
        return uniqueLabels;
    }

    private static Map<Integer, Integer> calculateVoxelCounts(ImagePlus labelImage) {
        Map<Integer, Integer> voxelCounts = new HashMap<>();
        ImageStack stack = labelImage.getStack();
        int width = labelImage.getWidth();
        int height = labelImage.getHeight();
        int depth = labelImage.getNSlices();

        for (int z = 1; z <= depth; z++) {
            ImageProcessor processor = stack.getProcessor(z);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int label = (int) processor.getPixelValue(x, y);
                    if (label > 0) {
                        voxelCounts.put(label, voxelCounts.getOrDefault(label, 0) + 1);
                    }
                }
            }
        }
        return voxelCounts;
    }

    private static void displayStatisticsSummary(MergeStatistics stats, ImagePlus inputImage) {
        IJ.log(String.format("Input: %dx%dx%d stack",
                inputImage.getWidth(), inputImage.getHeight(), inputImage.getNSlices()));
        for (String line : stats.toSummaryString().split("\n")) {
            IJ.log(line);
        }
        IJ.log(String.format("Memory usage: %.1f MB",
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024.0 * 1024.0)));
    }
}
