package jp.yourorg.fiji_maxima_based_segmenter;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import jp.yourorg.fiji_maxima_based_segmenter.alg.*;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter3D;
import jp.yourorg.fiji_maxima_based_segmenter.ui.SeededSpotQuantifier3DFrame;
import jp.yourorg.fiji_maxima_based_segmenter.util.CsvExporter;

import java.io.File;
import java.util.List;

/**
 * Seeded_Spot_Quantifier_3D_ — Fiji plugin entry point.
 *
 * Two-pass seeded watershed segmentation:
 *   - Seed threshold (high) + size filter  → reliable seeds
 *   - Area threshold (low)                 → spot extent / domain
 *   - Seeded watershed splits domain regions per seed
 *
 * GUI mode:   run("Seeded Spot Quantifier 3D")
 * Macro mode: run("Seeded Spot Quantifier 3D",
 *               "area_threshold=200 seed_threshold=500
 *                min_vol=0.1 max_vol=50.0
 *                gaussian_blur=false gauss_xy=1.0 gauss_z=0.5
 *                connectivity=6 fill_holes=false
 *                output=[/path/to/dir]");
 */
public class Seeded_Spot_Quantifier_3D_ implements PlugIn {

    @Override
    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("Seeded Spot Quantifier 3D", "No image is open.");
            return;
        }
        if (imp.getNSlices() < 2) {
            IJ.error("Seeded Spot Quantifier 3D", "This plugin requires a 3D image stack.");
            return;
        }

        String options = Macro.getOptions();
        boolean isMacroMode = (options != null && !options.trim().isEmpty());

        if (!isMacroMode) {
            new SeededSpotQuantifier3DFrame(imp).setVisible(true);
        } else {
            runMacroMode(imp, options);
        }
    }

    private void runMacroMode(ImagePlus imp, String options) {
        try {
            int     areaThreshold = parseInt(options,  "area_threshold", 200);
            int     seedThreshold = parseInt(options,  "seed_threshold", 500);
            boolean areaEnabled   = parseBool(options, "area_enabled",   true);
            Double  minVol        = parseDoubleOpt(options, "min_vol");
            Double  maxVol        = parseDoubleOpt(options, "max_vol");
            boolean gauss         = parseBool(options, "gaussian_blur", false);
            double  gaussXY       = parseDouble(options, "gauss_xy", 1.0);
            double  gaussZ        = parseDouble(options, "gauss_z",  0.5);
            int     conn          = parseInt(options, "connectivity", 6);
            boolean fillHoles     = parseBool(options, "fill_holes", false);
            String  outputDir     = parseString(options, "output", null);

            // threshold field unused in SeededQuantifier3D (areaThreshold / seedThreshold used)
            QuantifierParams params = new QuantifierParams(
                areaThreshold, minVol, maxVol, gauss, gaussXY, gaussZ, conn, fillHoles);

            Calibration cal = imp.getCalibration();
            double vw = cal.pixelWidth  > 0 ? cal.pixelWidth  : 1;
            double vh = cal.pixelHeight > 0 ? cal.pixelHeight : 1;
            double vd = cal.pixelDepth  > 0 ? cal.pixelDepth  : 1;
            double voxelVol = vw * vh * vd;

            IJ.showStatus("Seeded Spot Quantifier 3D: segmenting...");
            SeededQuantifier3D.SeededResult result = SeededQuantifier3D.compute(
                imp, areaThreshold, seedThreshold, params, voxelVol, areaEnabled);
            if (result == null) {
                IJ.log("Seeded Spot Quantifier 3D: no spots detected.");
                return;
            }
            SegmentationResult3D seg = result.finalSeg;

            IJ.showStatus("Seeded Spot Quantifier 3D: measuring...");
            List<SpotMeasurement> spots = SpotMeasurer.measure(seg, imp, vw, vh, vd);

            File dir;
            if (outputDir != null && !outputDir.isEmpty()) {
                dir = new File(outputDir);
            } else {
                String path = imp.getOriginalFileInfo() != null
                    ? imp.getOriginalFileInfo().directory : null;
                dir = (path != null) ? new File(path) : new File(".");
            }
            if (!dir.exists()) dir.mkdirs();

            File csvDir = new File(dir, "csv");
            File roiDir = new File(dir, "roi");
            if (!csvDir.exists()) csvDir.mkdirs();
            if (!roiDir.exists()) roiDir.mkdirs();

            String basename = imp.getShortTitle().replaceAll("\\.tiff?$", "");

            File csvFile    = new File(csvDir, basename + "_spots.csv");
            File paramsFile = new File(dir, "params.txt");
            CsvExporter.writeCsv(spots, csvFile);
            CsvExporter.writeSeededParams(areaThreshold, seedThreshold, params, paramsFile);

            if (!spots.isEmpty()) {
                RoiManager rm = RoiManager.getRoiManager();
                rm.reset();
                new RoiExporter3D().exportToRoiManager(seg.labelImage);
                if (rm.getCount() > 0) {
                    File roiFile = new File(roiDir, basename + "_RoiSet.zip");
                    RoiExporter.saveRoiManagerToZip(roiFile.getAbsolutePath());
                }
            }

            IJ.log("Seeded Spot Quantifier 3D: " + spots.size() + " spot(s) → "
                + csvFile.getAbsolutePath());
            IJ.showStatus("Seeded Spot Quantifier 3D: done (" + spots.size() + " spots).");

        } catch (Exception ex) {
            IJ.error("Seeded Spot Quantifier 3D", "Error: " + ex.getMessage());
            IJ.handleException(ex);
        }
    }

    // ---- Argument parsing helpers ----

    private static int parseInt(String opts, String key, int def) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException ex) { return def; }
    }

    private static double parseDouble(String opts, String key, double def) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException ex) { return def; }
    }

    private static Double parseDoubleOpt(String opts, String key) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return null;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException ex) { return null; }
    }

    private static boolean parseBool(String opts, String key, boolean def) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        return Boolean.parseBoolean(v.trim());
    }

    private static String parseString(String opts, String key, String def) {
        String v = Macro.getValue(opts, key, null);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }
}
