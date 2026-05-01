package jp.yourorg.fiji_maxima_based_segmenter.alg;

import ij.ImagePlus;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SeededQuantifier3DTest {
    @Test
    public void maxOverlapKeepsOneAreaWhenOneSeedSpansMultipleAreaComponents() {
        QuantifierParams params = new QuantifierParams(
            50, null, null, false, 1.0, 0.5, 6, false,
            QuantifierParams.AreaConflictMode.MAX_OVERLAP);

        SeededQuantifier3D.SeededResult result = SeededQuantifier3D.compute(
            oneSeedTwoAreasImage(), 50, 10, params, 1.0, true);

        assertEquals(1, countLabels(result.finalSeg));
        assertEquals(1, countForegroundVoxels(result.finalSeg));
    }

    @Test
    public void splitKeepsEachAreaWhenOneSeedSpansMultipleAreaComponents() {
        QuantifierParams params = new QuantifierParams(
            50, null, null, false, 1.0, 0.5, 6, false,
            QuantifierParams.AreaConflictMode.SPLIT);

        SeededQuantifier3D.SeededResult result = SeededQuantifier3D.compute(
            oneSeedTwoAreasImage(), 50, 10, params, 1.0, true);

        assertEquals(2, countLabels(result.finalSeg));
        assertEquals(2, countForegroundVoxels(result.finalSeg));
    }

    private static ImagePlus oneSeedTwoAreasImage() {
        ShortProcessor ip = new ShortProcessor(5, 1);
        ip.set(0, 0, 60);
        ip.set(1, 0, 20);
        ip.set(2, 0, 20);
        ip.set(3, 0, 20);
        ip.set(4, 0, 55);
        return new ImagePlus("one-seed-two-areas", ip);
    }

    private static int countLabels(SegmentationResult3D seg) {
        Set<Integer> labels = new HashSet<Integer>();
        for (int z = 1; z <= seg.labelImage.getNSlices(); z++) {
            for (int y = 0; y < seg.labelImage.getHeight(); y++) {
                for (int x = 0; x < seg.labelImage.getWidth(); x++) {
                    int label = (int) Math.round(seg.labelImage.getStack().getProcessor(z).getPixelValue(x, y));
                    if (label > 0) labels.add(label);
                }
            }
        }
        return labels.size();
    }

    private static int countForegroundVoxels(SegmentationResult3D seg) {
        int count = 0;
        for (int z = 1; z <= seg.labelImage.getNSlices(); z++) {
            for (int y = 0; y < seg.labelImage.getHeight(); y++) {
                for (int x = 0; x < seg.labelImage.getWidth(); x++) {
                    int label = (int) Math.round(seg.labelImage.getStack().getProcessor(z).getPixelValue(x, y));
                    if (label > 0) count++;
                }
            }
        }
        return count;
    }
}
