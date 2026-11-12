package com.harman.gnssref.core;

/** One tracked satellite at one second. Pure data, no Android types. */
public final class SatelliteSample {
    public final String system;      // GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC, SBAS, Unknown
    public final int svid;
    public final float cn0;          // dB-Hz, 0 when no signal
    public final float elevation;    // degrees above horizon
    public final float azimuth;      // degrees from north
    public final boolean usedInFix;

    public SatelliteSample(String system, int svid, float cn0, float elevation, float azimuth, boolean usedInFix) {
        this.system = system;
        this.svid = svid;
        this.cn0 = cn0;
        this.elevation = elevation;
        this.azimuth = azimuth;
        this.usedInFix = usedInFix;
    }

    /** Quectel's two conditions for a satellite to take part in the position: C/N0 >= 32 dB-Hz and elevation > 5 deg. */
    public boolean isUsable() {
        return cn0 >= Metrics.USABLE_CN0 && elevation > Metrics.MIN_ELEVATION;
    }

    public boolean isStrong() {
        return cn0 >= Metrics.STRONG_CN0 && elevation > Metrics.MIN_ELEVATION;
    }

    public String key() {
        return system + ":" + svid;
    }

    @Override
    public String toString() {
        return system + " " + svid + " cn0=" + cn0 + " el=" + elevation + " az=" + azimuth + (usedInFix ? " used" : "");
    }
}
