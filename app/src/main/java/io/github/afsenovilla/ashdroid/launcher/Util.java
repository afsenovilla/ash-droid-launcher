package io.github.afsenovilla.ashdroid.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class Util
{
    private Util() {}

    static void deleteRecursive(File f)
    {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    static File findChildIgnoreCase(File dir, String name)
    {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.getName().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    static String readText(File f) throws IOException
    {
        try (InputStream in = new FileInputStream(f)) {
            return readText(in);
        }
    }

    static String readText(InputStream in) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        String s = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        if (s.startsWith("﻿")) s = s.substring(1);
        return s;
    }

    /** Atomic: writes a sibling temp file, syncs it, then renames it over {@code f}. */
    static void writeText(File f, String text) throws IOException
    {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Could not replace " + f.getName());
        }
    }

    static void copy(File from, File to) throws IOException
    {
        try (InputStream in = new FileInputStream(from); OutputStream os = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    /**
     * Removes // and /* *&#47; comments from mkxp.json (it's JSON with comments),
     * leaving string contents (e.g. URLs) untouched, and drops trailing commas.
     */
    static String stripJsonComments(String src)
    {
        StringBuilder out = new StringBuilder(src.length());
        boolean inStr = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inStr) {
                out.append(c);
                if (c == '\\' && i + 1 < src.length()) {
                    out.append(src.charAt(++i));
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
                out.append('\n');
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i++;
            } else {
                out.append(c);
            }
        }
        // trailing commas before } or ]
        return out.toString().replaceAll(",(\\s*[}\\]])", "$1");
    }
}
