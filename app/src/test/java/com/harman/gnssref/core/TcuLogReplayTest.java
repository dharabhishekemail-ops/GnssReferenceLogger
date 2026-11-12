package com.harman.gnssref.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays the Bangalore TCU NMEA log (19 Oct 2026) through the app's engine and checks the numbers against the
 * Python analysis used in the MSIL deck. If this test passes, the phone and the TCU reports use identical maths.
 */
public class TcuLogReplayTest {

    @Test public void replayMatchesTheDeckNumbers() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("bangalore_tcu.nmea");
        assertNotNull("test resource bangalore_tcu.nmea missing", in);
        SessionStats stats = new SessionStats();
        Map<String, Map<String, SatelliteSample>> gsv = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        long elapsed = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("$") || !NmeaParser.checksumOk(line)) continue;
                String type = NmeaParser.type(line);
                if ("GSV".equals(type)) {
                    String[] f = line.substring(0, line.lastIndexOf('*')).split(",", -1);
                    final String sig = (f.length - 4) % 4 == 1 ? f[f.length - 1] : "";
                    String system = Metrics.systemFromTalker(NmeaParser.talker(line));
                    Map<String, SatelliteSample> m = gsv.get(system);
                    if (m == null) { m = new LinkedHashMap<>(); gsv.put(system, m); }
                    if ("1".equals(f[2])) {
                        List<String> drop = new ArrayList<>();
                        for (String k : m.keySet()) if (k.endsWith("|" + sig)) drop.add(k);
                        for (String k : drop) m.remove(k);
                    }
                    for (SatelliteSample s : NmeaParser.parseGsv(line)) m.put(s.svid + "|" + sig, s);
                } else if ("GSA".equals(type)) {
                    used.addAll(NmeaParser.parseGsaUsedKeys(line));
                } else if ("GGA".equals(type)) {
                    NmeaParser.Gga gga = NmeaParser.parseGga(line);
                    List<SatelliteSample> raw = new ArrayList<>();
                    for (Map<String, SatelliteSample> m : gsv.values())
                        for (SatelliteSample s : m.values())
                            raw.add(new SatelliteSample(s.system, s.svid, s.cn0, s.elevation, s.azimuth, used.contains(s.key())));
                    List<SatelliteSample> sats = Metrics.dedupe(raw);
                    boolean fix = gga != null && gga.quality > 0;
                    EpochRecord r = Metrics.summarize(elapsed, 0L, sats, fix, gga == null ? Double.NaN : gga.lat, gga == null ? Double.NaN : gga.lon,
                            Double.NaN, Double.NaN, gga == null ? Double.NaN : gga.hdop, gga == null ? 0 : gga.quality);
                    stats.add(r, sats);
                    elapsed++;
                    used.clear();
                }
            }
        }
        // numbers from the Python analysis (MSIL deck, Bangalore reference)
        assertEquals(2737, stats.seconds());
        assertEquals(2692, stats.fixedSeconds());
        assertEquals(12.0, stats.used().avg(), 0.1);
        assertEquals(15.1, stats.usable().avg(), 0.1);
        assertEquals(29.3, stats.tracked().avg(), 0.1);
        assertEquals(41.6, stats.cn0Used().avg(), 0.1);
        assertEquals(33.2, stats.cn0All().avg(), 0.1);
        assertEquals(1.4, stats.hdop().median(), 0.05);
        assertEquals(91.0, stats.hdop().pctAtOrBelow(2), 0.2);
        assertEquals(7, (int) stats.usable().min());
        assertEquals(5, stats.firstFixElapsedS());
        assertEquals(11, stats.steadyFixElapsedS());
    }
}
