package jp.yourorg.fiji_maxima_based_segmenter.alg;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Measures 3D spot statistics by scanning the label image once (O(W×H×D)).
 *
 * For each valid label the following are computed:
 *   - volume in voxels and µm³
 *   - integrated intensity (sum of original pixel values within the spot)
 *   - mean intensity
 *   - centroid in µm (X, Y, Z)
 */
public class SpotMeasurer {

    /**
     * @param seg     Filtered SegmentationResult3D (invalid labels already zeroed)
     * @param origImp Original (unthresholded) image for intensity measurements
     * @param vw      Voxel width  (µm)
     * @param vh      Voxel height (µm)
     * @param vd      Voxel depth  (µm)
     * @return list of SpotMeasurement, one per valid label, sorted by label id
     */
    public static List<SpotMeasurement> measure(SegmentationResult3D seg, ImagePlus origImp,
                                                 double vw, double vh, double vd) {
        return measure(seg, origImp, vw, vh, vd, null);
    }

    public static List<SpotMeasurement> measure(SegmentationResult3D seg, ImagePlus origImp,
                                                 double vw, double vh, double vd,
                                                 BooleanSupplier shouldCancel) {
        ImageStack labelStack = seg.labelImage.getStack();
        ImageStack origStack  = origImp.getStack();
        int w = labelStack.getWidth();
        int h = labelStack.getHeight();
        int d = labelStack.getSize();

        // Per-label accumulators (use HashMap; labels may be sparse after filtering)
        Map<Integer, long[]>        voxCount = new HashMap<>();  // [0]
        Map<Integer, double[]>      intDen   = new HashMap<>();  // [0] = sum of intensity
        Map<Integer, double[]>      maxInt   = new HashMap<>();  // [0] = max intensity
        Map<Integer, double[]>      surface  = new HashMap<>();  // [0] = surface area
        Map<Integer, double[]>      sumX     = new HashMap<>();  // [0]
        Map<Integer, double[]>      sumY     = new HashMap<>();  // [0]
        Map<Integer, double[]>      sumZ     = new HashMap<>();  // [0]
        Map<Integer, List<int[]>>   voxels   = new HashMap<>();  // for max Feret diameter
        double yzFace = vh * vd;
        double xzFace = vw * vd;
        double xyFace = vw * vh;

        for (int z = 1; z <= d; z++) {
            checkCancelled(shouldCancel);
            ImageProcessor labelIp = labelStack.getProcessor(z);
            ImageProcessor origIp  = origStack.getProcessor(z);
            for (int y = 0; y < h; y++) {
                checkCancelled(shouldCancel);
                for (int x = 0; x < w; x++) {
                    int label = (int) Math.round(labelIp.getPixelValue(x, y));
                    if (label <= 0) continue;

                    double intensity = origIp.get(x, y); // unsigned 16-bit value

                    voxCount.computeIfAbsent(label, k -> new long[1])[0]   += 1;
                    intDen  .computeIfAbsent(label, k -> new double[1])[0] += intensity;
                    double[] maxHolder = maxInt.computeIfAbsent(label, k -> new double[]{Double.NEGATIVE_INFINITY});
                    if (intensity > maxHolder[0]) maxHolder[0] = intensity;
                    sumX    .computeIfAbsent(label, k -> new double[1])[0] += x;
                    sumY    .computeIfAbsent(label, k -> new double[1])[0] += y;
                    sumZ    .computeIfAbsent(label, k -> new double[1])[0] += (z - 1); // 0-based Z index
                    voxels  .computeIfAbsent(label, k -> new ArrayList<>()).add(new int[]{x, y, z - 1});

                    double exposed = 0.0;
                    if (x == 0 || (int) Math.round(labelIp.getPixelValue(x - 1, y)) != label) exposed += yzFace;
                    if (x == w - 1 || (int) Math.round(labelIp.getPixelValue(x + 1, y)) != label) exposed += yzFace;
                    if (y == 0 || (int) Math.round(labelIp.getPixelValue(x, y - 1)) != label) exposed += xzFace;
                    if (y == h - 1 || (int) Math.round(labelIp.getPixelValue(x, y + 1)) != label) exposed += xzFace;
                    if (z == 1 || (int) Math.round(labelStack.getProcessor(z - 1).getPixelValue(x, y)) != label) exposed += xyFace;
                    if (z == d || (int) Math.round(labelStack.getProcessor(z + 1).getPixelValue(x, y)) != label) exposed += xyFace;
                    surface.computeIfAbsent(label, k -> new double[1])[0] += exposed;
                }
            }
        }

        double voxelVol = vw * vh * vd;
        List<SpotMeasurement> result = new ArrayList<>();

        for (int label : new java.util.TreeSet<>(voxCount.keySet())) {
            checkCancelled(shouldCancel);
            long   nVox    = voxCount.get(label)[0];
            double volume  = nVox * voxelVol;
            double totalI  = intDen.get(label)[0];
            double maxI    = maxInt.get(label)[0];
            double surfA   = surface.get(label)[0];
            double sphereA = Math.cbrt(Math.PI) * Math.pow(6.0 * volume, 2.0 / 3.0);
            double sphericity = surfA > 0.0 ? sphereA / surfA : 0.0;
            double cx      = sumX.get(label)[0] / nVox;
            double cy      = sumY.get(label)[0] / nVox;
            double cz      = sumZ.get(label)[0] / nVox;

            double maxFeret = computeMaxFeret3D(voxels.get(label), vw, vh, vd);
            result.add(new SpotMeasurement(
                label,
                nVox,
                volume,
                surfA,
                sphericity,
                totalI,
                totalI / nVox,
                maxI,
                cx * vw,
                cy * vh,
                cz * vd,
                maxFeret
            ));
        }
        return result;
    }

    private static double computeMaxFeret3D(List<int[]> pts, double vw, double vh, double vd) {
        if (pts == null || pts.size() < 2) return 0.0;
        double maxDist2 = 0.0;
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            int[] a = pts.get(i);
            for (int j = i + 1; j < n; j++) {
                int[] b = pts.get(j);
                double dx = (a[0] - b[0]) * vw;
                double dy = (a[1] - b[1]) * vh;
                double dz = (a[2] - b[2]) * vd;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > maxDist2) maxDist2 = d2;
            }
        }
        return Math.sqrt(maxDist2);
    }

    private static void checkCancelled(BooleanSupplier shouldCancel) {
        if (shouldCancel != null && shouldCancel.getAsBoolean()) {
            throw new CancellationException();
        }
    }
}
