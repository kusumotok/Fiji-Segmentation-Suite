package jp.yourorg.fiji_maxima_based_segmenter.alg;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        ImageStack labelStack = seg.labelImage.getStack();
        ImageStack origStack  = origImp.getStack();
        int w = labelStack.getWidth();
        int h = labelStack.getHeight();
        int d = labelStack.getSize();

        // Per-label accumulators (use HashMap; labels may be sparse after filtering)
        Map<Integer, long[]>   voxCount = new HashMap<>();  // [0]
        Map<Integer, double[]> intDen   = new HashMap<>();  // [0] = sum of intensity
        Map<Integer, double[]> sumX     = new HashMap<>();  // [0]
        Map<Integer, double[]> sumY     = new HashMap<>();  // [0]
        Map<Integer, double[]> sumZ     = new HashMap<>();  // [0]

        for (int z = 1; z <= d; z++) {
            ImageProcessor labelIp = labelStack.getProcessor(z);
            ImageProcessor origIp  = origStack.getProcessor(z);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int label = (int) Math.round(labelIp.getPixelValue(x, y));
                    if (label <= 0) continue;

                    double intensity = origIp.get(x, y); // unsigned 16-bit value

                    voxCount.computeIfAbsent(label, k -> new long[1])[0]   += 1;
                    intDen  .computeIfAbsent(label, k -> new double[1])[0] += intensity;
                    sumX    .computeIfAbsent(label, k -> new double[1])[0] += x;
                    sumY    .computeIfAbsent(label, k -> new double[1])[0] += y;
                    sumZ    .computeIfAbsent(label, k -> new double[1])[0] += (z - 1); // 0-based Z index
                }
            }
        }

        double voxelVol = vw * vh * vd;
        List<SpotMeasurement> result = new ArrayList<>();

        for (int label : new java.util.TreeSet<>(voxCount.keySet())) {
            long   nVox    = voxCount.get(label)[0];
            double totalI  = intDen.get(label)[0];
            double cx      = sumX.get(label)[0] / nVox;
            double cy      = sumY.get(label)[0] / nVox;
            double cz      = sumZ.get(label)[0] / nVox;

            result.add(new SpotMeasurement(
                label,
                nVox,
                nVox * voxelVol,
                totalI,
                totalI / nVox,
                cx * vw,
                cy * vh,
                cz * vd
            ));
        }
        return result;
    }
}
