package com.harman.gnssref.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zips a session folder into the cache and opens the Android share sheet. */
public final class ZipShare {
    private ZipShare() {}

    public static File zipFolder(Context ctx, File dir) throws IOException {
        File out = new File(ctx.getCacheDir(), "share");
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("cannot create share dir");
        File zip = new File(out, dir.getName() + ".zip");
        byte[] buf = new byte[64 * 1024];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.isFile()) continue;
                    zos.putNextEntry(new ZipEntry(f.getName()));
                    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
                        int n;
                        while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                    }
                    zos.closeEntry();
                }
            }
        }
        return zip;
    }

    public static void share(Context ctx, File dir, String subject) throws IOException {
        File zip = zipFolder(ctx, dir);
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", zip);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, "GNSS reference session " + dir.getName() + " (per_second.csv, satellites.csv, raw.nmea, summary.txt, photo if taken).");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(i, "Share session"));
    }
}
