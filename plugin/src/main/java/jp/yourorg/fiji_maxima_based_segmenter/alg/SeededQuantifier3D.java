package jp.yourorg.fiji_maxima_based_segmenter.alg;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.core.Connectivity;
import jp.yourorg.fiji_maxima_based_segmenter.core.MarkerResult3D;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
     * Compound result holding seed segmentation (raw + filtered) and the final segmentation.
     * When areaEnabled=false, seedSeg and finalSeg point to the same object.
     */
    public static class SeededResult {
        /** All seed CC labels before size filter (for Seed ROI export). */
        public final SegmentationResult3D rawSeedSeg;
        /** Size-filtered seed labels (from seed threshold CC). */
        public final SegmentationResult3D seedSeg;
        /** Final segmentation (watershed if area enabled, otherwise same as seedSeg). */
        public final SegmentationResult3D finalSeg;

        public SeededResult(SegmentationResult3D rawSeedSeg,
                            SegmentationResult3D seedSeg,
                            SegmentationResult3D finalSeg) {
            this.rawSeedSeg = rawSeedSeg;
            this.seedSeg    = seedSeg;
            this.finalSeg   = finalSeg;
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
        return compute(imp, areaThreshold, seedThreshold, params, voxelVol, areaEnabled, null, null);
    }

    public static SeededResult compute(ImagePlus imp,
                                       int areaThreshold,
                                       int seedThreshold,
                                       QuantifierParams params,
                                       double voxelVol,
                                       boolean areaEnabled,
                                       Consumer<String> progress) {
        return compute(imp, areaThreshold, seedThreshold, params, voxelVol, areaEnabled, progress, null);
    }

    public static SeededResult compute(ImagePlus imp,
                                       int areaThreshold,
                                       int seedThreshold,
                                       QuantifierParams params,
                                       double voxelVol,
                                       boolean areaEnabled,
                                       Consumer<String> progress,
                                       BooleanSupplier shouldCancel) {
        int w = imp.getWidth();
        int h = imp.getHeight();
        int d = imp.getNSlices();

        // 1. Apply Gaussian blur once (if enabled) on a working copy
        ImagePlus blurred = imp;
        if (params.gaussianBlur) {
            reportProgress(progress, "blurring");
            checkCancelled(shouldCancel);
            blurred = imp.duplicate();
            blurred.setTitle(imp.getShortTitle() + "-blurred");
            IJ.run(blurred, "Gaussian Blur 3D...",
                "x=" + params.gaussXY + " y=" + params.gaussXY + " z=" + params.gaussZ);
            checkCancelled(shouldCancel);
        }

        try {
            // 2. Seed detection: CC at seedThreshold + size filter
            reportProgress(progress, "finding seed components");
            CcResult3D seedCC = SpotQuantifier3D.computeCCFromBlurred(blurred, seedThreshold, params, shouldCancel);
            if (seedCC.voxelCounts.isEmpty()) {
                return null;
            }
            // Raw seeds: all CC labels marked valid (no size filter)
            Map<Integer, Integer> allValid = new HashMap<>();
            seedCC.voxelCounts.keySet().forEach(k -> allValid.put(k, CcResult3D.STATUS_VALID));
            SegmentationResult3D rawSeedSeg = seedCC.buildFilteredResult(allValid);

            reportProgress(progress, "filtering seed components");
            checkCancelled(shouldCancel);
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
                return new SeededResult(rawSeedSeg, filteredSeeds, filteredSeeds);
            }

            // 4. Build domain mask at areaThreshold
            reportProgress(progress, "building area mask");
            ImageStack blurredStack = blurred.getStack();
            ImageStack domainStack  = new ImageStack(w, h);
            for (int z = 1; z <= d; z++) {
                checkCancelled(shouldCancel);
                ImageProcessor ip = blurredStack.getProcessor(z);
                ByteProcessor bp  = new ByteProcessor(w, h);
                byte[] bpix = (byte[]) bp.getPixels();
                for (int y = 0; y < h; y++) {
                    checkCancelled(shouldCancel);
                    for (int x = 0; x < w; x++) {
                        if (ip.get(x, y) >= areaThreshold) {
                            bpix[y * w + x] = (byte) 255;
                        }
                    }
                }
                domainStack.addSlice(bp);
            }
            if (params.fillHoles) {
                reportProgress(progress, "filling mask holes");
                checkCancelled(shouldCancel);
                ImagePlus domainImp = new ImagePlus("domain", domainStack);
                IJ.run(domainImp, "Fill Holes", "stack");
                domainStack = domainImp.getStack();
                checkCancelled(shouldCancel);
            }

            // 5. Seeded watershed: expand seeds within domain
            reportProgress(progress, "running watershed");
            ImageStack seedLabelStack = filteredSeeds.labelImage.getStack();
            Connectivity conn = Connectivity.fromInt(params.connectivity);
            MarkerResult3D markers = new MarkerResult3D(seedLabelStack, domainStack, seedCount);
            SegmentationResult3D watershedResult = new Watershed3DRunner().run(blurred, markers, conn, shouldCancel);
            return new SeededResult(rawSeedSeg, filteredSeeds, watershedResult);

        } finally {
            if (params.gaussianBlur && blurred != imp) {
                blurred.close();
            }
        }
    }

    private static void reportProgress(Consumer<String> progress, String message) {
        if (progress != null) progress.accept(message);
    }

    private static void checkCancelled(BooleanSupplier shouldCancel) {
        if (shouldCancel != null && shouldCancel.getAsBoolean()) {
            throw new CancellationException();
        }
    }
}
