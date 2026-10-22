package io.github.afsenovilla.ashdroid.launcher;

import android.graphics.Bitmap;

/**
 * Tiny 5x7 pixel font for labels on small key caps (A, B, X, Y, L1, R2,
 * START…). TrueType fonts don't render cleanly at 7px; this does, and matches
 * the blocky lettering of Pokémon Essentials' own key icons.
 */
final class PixelFont
{
    static final int W = 5, H = 7;

    private PixelFont() {}

    private static String[] glyph(char ch)
    {
        switch (Character.toUpperCase(ch)) {
            case 'A': return new String[] { ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#" };
            case 'B': return new String[] { "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####." };
            case 'C': return new String[] { ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###." };
            case 'D': return new String[] { "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####." };
            case 'E': return new String[] { "#####", "#....", "#....", "####.", "#....", "#....", "#####" };
            case 'F': return new String[] { "#####", "#....", "#....", "####.", "#....", "#....", "#...." };
            case 'G': return new String[] { ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####" };
            case 'H': return new String[] { "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#" };
            case 'I': return new String[] { "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####" };
            case 'J': return new String[] { "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.." };
            case 'K': return new String[] { "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#" };
            case 'L': return new String[] { "#....", "#....", "#....", "#....", "#....", "#....", "#####" };
            case 'M': return new String[] { "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#" };
            case 'N': return new String[] { "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#" };
            case 'O': return new String[] { ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###." };
            case 'P': return new String[] { "####.", "#...#", "#...#", "####.", "#....", "#....", "#...." };
            case 'Q': return new String[] { ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#" };
            case 'R': return new String[] { "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#" };
            case 'S': return new String[] { ".####", "#....", "#....", ".###.", "....#", "....#", "####." };
            case 'T': return new String[] { "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.." };
            case 'U': return new String[] { "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###." };
            case 'V': return new String[] { "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.." };
            case 'W': return new String[] { "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#" };
            case 'X': return new String[] { "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#" };
            case 'Y': return new String[] { "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.." };
            case 'Z': return new String[] { "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####" };
            case '0': return new String[] { ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###." };
            case '1': return new String[] { "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###." };
            case '2': return new String[] { ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####" };
            case '3': return new String[] { "####.", "....#", "....#", ".###.", "....#", "....#", "####." };
            case '4': return new String[] { "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#." };
            case '5': return new String[] { "#####", "#....", "####.", "....#", "....#", "#...#", ".###." };
            case '/': return new String[] { "....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...." };
            default:  return new String[] { ".....", ".....", ".....", ".....", ".....", ".....", "....." };
        }
    }

    /** Width in pixels of a label at the given scale (bold: 2px strokes, like Essentials' key icons). */
    static int width(String s, int scale)
    {
        return s.isEmpty() ? 0 : (s.length() * (W + 2) - 1) * scale;
    }

    /** Draws `s` with its top-left corner at (x, y). Optional 1px shadow (down-right). */
    static void draw(Bitmap b, String s, int x, int y, int scale, int color, int shadow)
    {
        for (int pass = shadow != 0 ? 0 : 1; pass < 2; pass++) {
            int off = pass == 0 ? scale : 0;
            int col = pass == 0 ? shadow : color;
            int cx = x;
            for (int i = 0; i < s.length(); i++) {
                String[] g = glyph(s.charAt(i));
                for (int gy = 0; gy < H; gy++)
                    for (int gx = 0; gx < W; gx++)
                        if (g[gy].charAt(gx) == '#') {
                            fill(b, cx + gx * scale + off, y + gy * scale + off, scale, col);
                            fill(b, cx + (gx + 1) * scale + off, y + gy * scale + off, scale, col); // bold
                        }
                cx += (W + 2) * scale;
            }
        }
    }

    /** Width of a label drawn with {@link #drawOutlined} (regular weight, 1px gaps, 1px outline). */
    static int widthOutlined(String s)
    {
        return s.isEmpty() ? 0 : s.length() * (W + 1) - 1 + 2;
    }

    /**
     * Draws `s` stretched to `height` pixels tall (rows repeated evenly) with a
     * 1px outline all around, like the lettering of Essentials' battle buttons.
     * (x, y) is the top-left of the outline box.
     */
    static void drawOutlined(Bitmap b, String s, int x, int y, int height, int color, int outline)
    {
        int gh = Math.max(H, height);
        for (int pass = 0; pass < 2; pass++) {
            int cx = x + 1;
            for (int i = 0; i < s.length(); i++) {
                String[] g = glyph(s.charAt(i));
                for (int ty = 0; ty < gh; ty++) {
                    int gy = ty * H / gh;
                    for (int gx = 0; gx < W; gx++) {
                        if (g[gy].charAt(gx) != '#') continue;
                        int px = cx + gx, py = y + 1 + ty;
                        if (pass == 0) {
                            for (int dy = -1; dy <= 1; dy++)
                                for (int dx = -1; dx <= 1; dx++) fill(b, px + dx, py + dy, 1, outline);
                        } else {
                            fill(b, px, py, 1, color);
                        }
                    }
                }
                cx += W + 1;
            }
        }
    }

    private static void fill(Bitmap b, int x, int y, int s, int c)
    {
        for (int yy = y; yy < y + s; yy++)
            for (int xx = x; xx < x + s; xx++)
                if (xx >= 0 && yy >= 0 && xx < b.getWidth() && yy < b.getHeight()) b.setPixel(xx, yy, c);
    }
}
