package jp.yourorg.fiji_maxima_based_segmenter;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.plugin.PlugIn;
import jp.yourorg.fiji_maxima_based_segmenter.alg.QuantifierParams;
import jp.yourorg.fiji_maxima_based_segmenter.ui.SeededSpotQuantifier3DFrame;
import jp.yourorg.fiji_maxima_based_segmenter.util.SeededSpotQuantifier3DImageSupport;
import jp.yourorg.fiji_maxima_based_segmenter.util.SeededSpotQuantifier3DSaveSupport;

import java.io.File;

/**
 * Seeded Spot Quantifier 3D plugin entry point.
 */
public class Seeded_Spot_Quantifier_3D_ implements PlugIn {

    @Override
    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        String options = Macro.getOptions();
        boolean isMacroMode = (options != null && !options.trim().isEmpty());

        if (!isMacroMode) {
            new SeededSpotQuantifier3DFrame(imp).setVisible(true);
            return;
        }

        if (imp == null) {
            IJ.error("Seeded Spot Quantifier 3D", "No image is open.");
            return;
        }
        if (imp.getNSlices() < 2) {
            IJ.error("Seeded Spot Quantifier 3D", "This plugin requires a 3D image stack.");
            return;
        }
        runMacroMode(imp, options);
    }

    private void runMacroMode(ImagePlus imp, String options) {
        ImagePlus procImp = null;
        boolean ownsProcImp = false;
        try {
            int areaThreshold = parseInt(options, "area_threshold", 200);
            int seedThreshold = parseInt(options, "seed_threshold", 500);
            boolean areaEnabled = parseBool(options, "area_enabled", true);
            Double minVol = parseDoubleOpt(options, "min_vol");
            Double maxVol = parseDoubleOpt(options, "max_vol");
            boolean gauss = parseBool(options, "gaussian_blur", false);
            double gaussXY = parseDouble(options, "gauss_xy", 1.0);
            double gaussZ = parseDouble(options, "gauss_z", 0.5);
            int conn = parseInt(options, "connectivity", 6);
            boolean fillHoles = parseBool(options, "fill_holes", false);
            int channel = parseInt(options, "channel", Math.max(1, imp.getC()));
            boolean saveSeedRoi = parseBool(options, "save_seed_roi", false);
            boolean saveSizeRoi = parseBool(options, "save_size_roi", false);
            boolean saveAreaRoi = parseBool(options, "save_area_roi", false);
            boolean saveResultRoi = parseBool(options, "save_result_roi", false);
            boolean saveResultRoiByObject = parseBool(options, "save_result_roi_by_object", true);
            boolean saveCsv = parseBool(options, "save_csv", true);
            boolean saveParam = parseBool(options, "save_param", true);
            boolean customFolder = parseBool(options, "custom_folder", false);
            String folderPattern = parseString(options, "folder_pattern", "{name} result");
            String outputDir = parseString(options, "output", null);

            QuantifierParams params = new QuantifierParams(
                areaThreshold, minVol, maxVol, gauss, gaussXY, gaussZ, conn, fillHoles);

            int selectedChannel = Math.max(1, Math.min(Math.max(1, imp.getNChannels()), channel));
            procImp = SeededSpotQuantifier3DImageSupport.extractProcessingImage(imp, selectedChannel);
            ownsProcImp = (procImp != imp);

            File explicitBaseDir = (outputDir == null || outputDir.isEmpty()) ? null : new File(outputDir);
            File outDir = SeededSpotQuantifier3DSaveSupport.resolveOutputDir(
                null, imp, customFolder, folderPattern, seedThreshold, areaThreshold, explicitBaseDir);
            if (outDir == null) {
                IJ.showStatus("Seeded Spot Quantifier 3D: cancelled.");
                return;
            }

            String err = SeededSpotQuantifier3DSaveSupport.saveOneToDir(
                procImp, imp, selectedChannel,
                areaThreshold, seedThreshold, areaEnabled, params, outDir,
                saveSeedRoi, saveSizeRoi, saveAreaRoi, saveResultRoi, saveResultRoiByObject,
                saveCsv, saveParam, null,
                stage -> IJ.showStatus("Seeded Spot Quantifier 3D: " + stage));
            if (SeededSpotQuantifier3DSaveSupport.CANCELLED.equals(err)) {
                IJ.showStatus("Seeded Spot Quantifier 3D: cancelled.");
                return;
            }
            if (err != null) {
                IJ.error("Seeded Spot Quantifier 3D", err);
                return;
            }

            IJ.log("Seeded Spot Quantifier 3D: saved to " + outDir.getAbsolutePath());
            IJ.showStatus("Seeded Spot Quantifier 3D: saved to " + outDir.getName());
        } catch (Exception ex) {
            IJ.error("Seeded Spot Quantifier 3D", "Error: " + ex.getMessage());
            IJ.handleException(ex);
        } finally {
            SeededSpotQuantifier3DImageSupport.disposeProcessingImage(procImp, ownsProcImp);
        }
    }

    private static int parseInt(String opts, String key, int def) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static double parseDouble(String opts, String key, double def) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static Double parseDoubleOpt(String opts, String key) {
        String v = Macro.getValue(opts, key, null);
        if (v == null || v.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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
