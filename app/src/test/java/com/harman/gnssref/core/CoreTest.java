package com.harman.gnssref.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CoreTest {

    @Test public void dedupeKeepsStrongestSignalPerSatellite() {
        List<SatelliteSample> raw = Arrays.asList(
                new SatelliteSample("GPS", 5, 38f, 40f, 90f, false),   // L1
                new SatelliteSample("GPS", 5, 42f, 40f, 90f, true),    // L5, used
                new SatelliteSample("GPS", 7, 0f, 10f, 200f, false),   // no signal -> dropped
                new SatelliteSample("BeiDou", 5, 30f, 20f, 10f, false));
        List<SatelliteSample> d = Metrics.dedupe(raw);
        assertEquals(2, d.size());
        SatelliteSample gps5 = d.get(0);
        assertEquals(42f, gps5.cn0, 0.01);
        assertTrue(gps5.usedInFix);
        assertEquals("BeiDou", d.get(1).system);
    }

    @Test public void usableNeedsSignalAndElevation() {
        assertTrue(new SatelliteSample("GPS", 1, 32f, 6f, 0f, false).isUsable());
        assertFalse(new SatelliteSample("GPS", 1, 31.9f, 60f, 0f, false).isUsable());
        assertFalse(new SatelliteSample("GPS", 1, 45f, 5f, 0f, false).isUsable());
        assertTrue(new SatelliteSample("GPS", 1, 40f, 6f, 0f, false).isStrong());
    }

    @Test public void summarizeCountsTheFourSteps() {
        List<SatelliteSample> sats = Arrays.asList(
                new SatelliteSample("GPS", 1, 45f, 60f, 0f, true),
                new SatelliteSample("GPS", 2, 35f, 30f, 0f, true),
                new SatelliteSample("GPS", 3, 33f, 10f, 0f, false),
                new SatelliteSample("GLONASS", 70, 25f, 50f, 0f, false),
                new SatelliteSample("Galileo", 4, 41f, 3f, 0f, false));
        EpochRecord r = Metrics.summarize(10, 0L, sats, true, 12.9, 77.7, 4.0, 10.0, 1.2, 1);
        assertEquals(5, r.tracked);
        assertEquals(3, r.usable);
        assertEquals(1, r.strong);
        assertEquals(2, r.used);
        assertEquals((45 + 35 + 33 + 25 + 41) / 5.0, r.cn0AllAvg, 1e-6);
        assertEquals(40.0, r.cn0UsedAvg, 1e-6);
        assertEquals(1.2, r.hdop, 1e-9);
    }

    @Test public void nmeaChecksumAndGga() {
        String gga = "$GNGGA,091137.00,1258.42112,N,07742.71068,E,1,09,1.53,912.4,M,-86.0,M,,*4A";
        // recompute checksum to make the test independent of the literal
        String body = gga.substring(1, gga.lastIndexOf('*'));
        int x = 0; for (char ch : body.toCharArray()) x ^= ch;
        gga = "$" + body + "*" + String.format("%02X", x);
        assertTrue(NmeaParser.checksumOk(gga));
        assertFalse(NmeaParser.checksumOk(gga.replace("*", "*0")));
        NmeaParser.Gga g = NmeaParser.parseGga(gga);
        assertNotNull(g);
        assertEquals(1, g.quality);
        assertEquals(9, g.numSv);
        assertEquals(1.53, g.hdop, 1e-9);
        assertEquals(12.9736853, g.lat, 1e-6);
        assertEquals(77.7118447, g.lon, 1e-6);
        assertNull(NmeaParser.parseGga("$GNRMC,091137.00,A,1258.42112,N,07742.71068,E,0.0,0.0,191026,,,A*00"));
    }

    @Test public void nmeaGsvAndGsa() {
        List<SatelliteSample> s = NmeaParser.parseGsv("$GPGSV,3,1,12,02,45,120,38,05,10,200,,13,60,30,42,15,05,90,31,1*6E");
        assertEquals(4, s.size());
        assertEquals("GPS", s.get(0).system);
        assertEquals(38f, s.get(0).cn0, 0.01);
        assertEquals(0f, s.get(1).cn0, 0.01);          // empty C/N0 -> 0, dropped later by dedupe
        List<String> used = NmeaParser.parseGsaUsedKeys("$GNGSA,A,3,02,13,05,,,,,,,,,,1.8,1.0,1.5,1*00");
        assertEquals(Arrays.asList("GPS:2", "GPS:13", "GPS:5"), used);
    }

    @Test public void statMedianAndPct() {
        Stat s = new Stat();
        for (double v : new double[]{1, 2, 3, 4, 10, Double.NaN}) s.add(v);
        assertEquals(5, s.count());
        assertEquals(3.0, s.median(), 1e-9);
        assertEquals(80.0, s.pctAtOrBelow(4), 1e-9);
        assertEquals(1.0, s.min(), 1e-9);
        assertEquals(10.0, s.max(), 1e-9);
    }

    @Test public void csvRoundTrip() {
        EpochRecord r = new EpochRecord(42, 1760000000000L, true, 20, 13, 7, 9, 37.3, 44.56, 1.5, 3.2, 12.9748586, 77.711784, 2.5, 1);
        String line = CsvFormat.line(r);
        EpochRecord back = CsvFormat.parse(line);
        assertNotNull(back);
        assertEquals(r.elapsedS, back.elapsedS);
        assertEquals(r.used, back.used);
        assertEquals(r.usable, back.usable);
        assertEquals(r.tracked, back.tracked);
        assertEquals(r.hdop, back.hdop, 1e-9);
        assertEquals(r.lat, back.lat, 1e-7);
        assertTrue(back.fixValid);
        assertEquals(16, line.split(",", -1).length);
    }

    @Test public void sessionStatsStartupAndLosses() {
        SessionStats st = new SessionStats();
        List<SatelliteSample> none = new ArrayList<>();
        for (int t = 0; t < 40; t++) {
            boolean fix = t >= 5 && t != 9 && t != 10;     // first fix at 5, brief drop at 9-10, steady from 11
            st.add(new EpochRecord(t, 0L, fix, 20, 12, 6, fix ? 10 : 0, 33, fix ? 41 : Double.NaN, fix ? 1.2 : Double.NaN, Double.NaN, 0, 0, 0, fix ? 1 : 0), none);
        }
        assertEquals(5, st.firstFixElapsedS());
        assertEquals(11, st.steadyFixElapsedS());
        assertEquals(1, st.lossesAfterFirstFix());
        assertEquals(2, st.secondsWithoutFixAfterFirst());
        assertEquals(33, st.fixedSeconds());
        assertEquals(100.0, st.usedAtLeastPct(4), 1e-9);
    }
}
