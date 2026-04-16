package jp.yourorg.fiji_maxima_based_segmenter.alg;

public class SpotMeasurement {
    public final int    id;
    public final long   volumeVox;
    public final double volumeUm3;
    public final double surfaceAreaUm2;
    public final double sphericity;
    public final double integratedIntensity;
    public final double meanIntensity;
    public final double maxIntensity;
    public final double centroidXUm;
    public final double centroidYUm;
    public final double centroidZUm;

    public SpotMeasurement(int id, long volumeVox, double volumeUm3, double surfaceAreaUm2,
                            double sphericity,
                            double integratedIntensity, double meanIntensity, double maxIntensity,
                            double centroidXUm, double centroidYUm, double centroidZUm) {
        this.id                  = id;
        this.volumeVox           = volumeVox;
        this.volumeUm3           = volumeUm3;
        this.surfaceAreaUm2      = surfaceAreaUm2;
        this.sphericity          = sphericity;
        this.integratedIntensity = integratedIntensity;
        this.meanIntensity       = meanIntensity;
        this.maxIntensity        = maxIntensity;
        this.centroidXUm         = centroidXUm;
        this.centroidYUm         = centroidYUm;
        this.centroidZUm         = centroidZUm;
    }
}
