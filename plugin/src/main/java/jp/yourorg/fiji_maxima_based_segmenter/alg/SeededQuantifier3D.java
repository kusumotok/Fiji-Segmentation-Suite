package jp.yourorg.fiji_maxima_based_segmenter.alg;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.core.Connectivity;
import jp.yourorg.fiji_maxima_based_segmenter.core.MarkerResult3D;

import java.util.Map;

/**
 * Two-pass seeded watershed segmentation for Seeded Spot Quantifier 3D.
 *
 * Algorithm:
 *  1. [Optional] Gaussian blur once on a working copy
 *  2. Seed detection: CC at seedThreshold + size filter (same logic as SpotQuantifier3D)
 *  3. If areaEnabled: build domain mask at areaThreshold, run seeded watershed
 *     If areaEnabled=false: return seed CC directly (watershed bypass)
 *
 * When multiple seeds fall in the same domain region, watershed splits them.
 * Domain regions that contain no seed are excluded from the result.
 */
public class SeededQuantifier3D {

    /**
     * Compound result holding both the seed segmentation and the final segmentation.
     * When areaEnabled=false, seedSeg and finalSeg point to the same object.
     */
    public static class SeededResult {
        /** Size-filtered seed labels (from seed threshold CC). */
        public final SegmentationResult3D seedSeg;
        /** Final segmentation (watershed if area enabled, otherwise same as seedSeg). */
        public final SegmentationResult3D finalSeg;

        public SeededResult(SegmentationResult3D seedSeg, SegmentationResult3D finalSeg) {
            this.seedSeg  = seedSeg;
            this.finalSeg = finalSeg;
        }
    }

    /**
     * Run two-pass seeded segmentation.
     *
     * @param imp            Input 3D image
     * @param areaThreshold  Low threshold defining the extent of each spot (domain)
     * @param seedThreshold  High threshold for seed detection
     * @param params         Quantifier params (size filter, gauss, connectivity, fillHoles).
     *                       params.threshold is ignored; areaThreshold / seedThreshold are used.
     * @param voxelVol       µm³ per voxel (for seed size filter)
     * @param areaEnabled    If false, watershed is skipped and seed CC is used as final result
     * @return SeededResult with seedSeg and finalSeg, or null if no seeds found
     */
    public static SeededResult compute(ImagePlus imp,
                                       int areaThreshold,
                                       int seedThreshold,
                                       QuantifierParams params,
                                       double voxelVol,
                                       boolean areaEnabled) {
        int w = imp.getWidth();
        int h = imp.getHeight();
        int d = imp.getNSlices();

        // 1. Apply Gaussian blur once (if enabled) on a working copy
        ImagePlus blurred = imp;
        if (params.gaussianBlur) {
            blurred = imp.duplicate();
            blurred.setTitle(imp.getShortTitle() + "-blurred");
            IJ.run(blurred, "Gaussian Blur 3D...",
                "x=" + params.gaussXY + " y=" + params.gaussXY + " z=" + params.gaussZ);
        }

        try {
            // 2. Seed detection: CC at seedThreshold + size filter
            CcResult3D seedCC = SpotQuantifier3D.computeCCFromBlurred(blurred, seedThreshold, params);
            if (seedCC.voxelCounts.isEmpty()) {
                return null;
            }
            Map<Integer, Integer> seedStatus = seedCC.classifyLabels(params, voxelVol);
            SegmentationResult3D filteredSeeds = seedCC.buildFilteredResult(seedStatus);

            // Count valid seeds
            int seedCount = 0;
            for (int st : seedStatus.values()) {
                if (st == CcResult3D.STATUS_VALID) seedCount++;
            }
            if (seedCount == 0) {
                return null;
            }

            // 3. If area disabled: bypass watershed, return seeds as final result
            if (!areaEnabled) {
                return new SeededResult(filteredSeeds, filteredSeeds);
            }

            // 4. Build domain mask at areaThreshold
            ImageStack blurredStack = blurred.getStack();
            ImageStack domainStack  = new ImageStack(w, h);
            for (int z = 1; z <= d; z++) {
                ImageProcessor ip = blurredStack.getProcessor(z);
                ByteProcessor bp  = new ByteProcessor(w, h);
                byte[] bpix = (byte[]) bp.getPixels();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (ip.get(x, y) >= areaThreshold) {
                            bpix[y * w + x] = (byte) 255;
                        }
                    }
                }
                domainStack.addSlice(bp);
            }
            if (params.fillHoles) {
                ImagePlus domainImp = new ImagePlus("domain", domainStack);
                IJ.run(domainImp, "Fill Holes", "stack");
                domainStack = domainImp.getStack();
            }

            // 5. Seeded watershed: expand seeds within domain
            ImageStack seedLabelStack = filteredSeeds.labelImage.getStack();
            Connectivity conn = Connectivity.fromInt(params.connectivity);
            MarkerResult3D markers = new MarkerResult3D(seedLabelStack, domainStack, seedCount);
            SegmentationResult3D watershedResult = new Watershed3DRunner().run(blurred, markers, conn);
            return new SeededResult(filteredSeeds, watershedResult);

        } finally {
            if (params.gaussianBlur && blurred != imp) {
                blurred.close();
            }
        }
    }
}
