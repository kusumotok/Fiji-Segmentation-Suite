package jp.yourorg.fiji_maxima_based_segmenter.util;

import jp.yourorg.fiji_maxima_based_segmenter.alg.QuantifierParams;
import jp.yourorg.fiji_maxima_based_segmenter.alg.SpotMeasurement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes spot quantification results to CSV and saves parameters as a .env-style .txt file.
 */
public class CsvExporter {

    private static final String CSV_HEADER =
        "spot_id,volume_um3,volume_vox,integrated_intensity,mean_intensity," +
        "centroid_x_um,centroid_y_um,centroid_z_um";

    /**
     * Write spot measurements to a CSV file.
     *
     * @param spots     List of measurements (one row per spot)
     * @param out       Destination file (e.g. "[basename]_spots.csv")
     * @throws IOException on write error
     */
    public static void writeCsv(List<SpotMeasurement> spots, File out) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)))) {
            pw.println(CSV_HEADER);
            for (SpotMeasurement s : spots) {
                pw.printf("%d,%.6f,%d,%.2f,%.4f,%.4f,%.4f,%.4f%n",
                    s.id,
                    s.volumeUm3,
                    s.volumeVox,
                    s.integratedIntensity,
                    s.meanIntensity,
                    s.centroidXUm,
                    s.centroidYUm,
                    s.centroidZUm);
            }
        }
    }

    /**
     * Write parameters to a .env-style text file.
     * Empty value (KEY=) means the parameter is disabled / null.
     *
     * @param params  Quantifier parameters
     * @param out     Destination file (e.g. "params.txt")
     * @throws IOException on write error
     */
    /**
     * Write seeded-watershed parameters to a .env-style text file.
     *
     * @param areaThreshold  Low threshold defining the spot domain
     * @param seedThreshold  High threshold for seed detection
     * @param params         Quantifier params (size filter, gauss, connectivity, fillHoles)
     * @param out            Destination file
     */
    public static void writeSeededParams(int areaThreshold, int seedThreshold,
                                          QuantifierParams params, File out) throws IOException {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)))) {
            pw.println("# Seeded Spot Quantifier 3D -- session params");
            pw.println("# TIMESTAMP=" + timestamp);
            pw.println("AREA_THRESHOLD=" + areaThreshold);
            pw.println("SEED_THRESHOLD=" + seedThreshold);
            pw.println("MIN_VOL_UM3=" + (params.minVolUm3 != null ? params.minVolUm3 : ""));
            pw.println("MAX_VOL_UM3=" + (params.maxVolUm3 != null ? params.maxVolUm3 : ""));
            pw.println("GAUSSIAN_BLUR=" + params.gaussianBlur);
            pw.println("GAUSS_XY=" + (params.gaussianBlur ? params.gaussXY : ""));
            pw.println("GAUSS_Z="  + (params.gaussianBlur ? params.gaussZ  : ""));
            pw.println("CONNECTIVITY=" + params.connectivity);
            pw.println("FILL_HOLES="   + params.fillHoles);
        }
    }

    public static void writeParams(QuantifierParams params, File out) throws IOException {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)))) {
            pw.println("# Spot Quantifier 3D -- session params");
            pw.println("# TIMESTAMP=" + timestamp);
            pw.println("THRESHOLD=" + params.threshold);
            pw.println("MIN_VOL_UM3=" + (params.minVolUm3 != null ? params.minVolUm3 : ""));
            pw.println("MAX_VOL_UM3=" + (params.maxVolUm3 != null ? params.maxVolUm3 : ""));
            pw.println("GAUSSIAN_BLUR=" + params.gaussianBlur);
            pw.println("GAUSS_XY=" + (params.gaussianBlur ? params.gaussXY : ""));
            pw.println("GAUSS_Z="  + (params.gaussianBlur ? params.gaussZ  : ""));
            pw.println("CONNECTIVITY=" + params.connectivity);
            pw.println("FILL_HOLES="   + params.fillHoles);
        }
    }
}
