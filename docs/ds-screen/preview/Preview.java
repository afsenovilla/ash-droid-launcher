// Desktop preview of the second screen: draws a state written by the game-side patch with the very same
// DsScene / DsArt code the app uses, through Java2D instead of Android. Not part of the app.
// Usage: java -cp <classes> Preview <pixelfonts.txt> <state.json> <out.png>
import io.github.afsenovilla.ashdroid.companion.DsArt;
import io.github.afsenovilla.ashdroid.companion.DsJson;
import io.github.afsenovilla.ashdroid.companion.DsScene;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class Preview implements DsScene.Gfx
{
    private final BufferedImage out = new BufferedImage(DsScene.W, DsScene.H, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D g = out.createGraphics();
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<String, Font> fonts = new HashMap<>();
    private final DsScene scene;
    private Shape savedClip;

    private Preview(DsScene scene)
    {
        this.scene = scene;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    private BufferedImage load(String file, String fx)
    {
        String key = file + "|" + fx;
        if (images.containsKey(key)) return images.get(key);
        BufferedImage img = null;
        try {
            BufferedImage raw = ImageIO.read(new File(file));
            img = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().drawImage(raw, 0, 0, null);
            if (!fx.isEmpty()) {
                int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
                DsScene.applyFx(px, fx);
                img.setRGB(0, 0, img.getWidth(), img.getHeight(), px, 0, img.getWidth());
            }
        } catch (Exception e) {
            System.err.println("missing image: " + file);
        }
        images.put(key, img);
        return img;
    }

    private Font font(int size, int which)
    {
        String key = which + ":" + size;
        Font f = fonts.get(key);
        if (f == null) {
            try {
                f = Font.createFont(Font.TRUETYPE_FONT, new File(scene.font(which))).deriveFont((float) size);
            } catch (Exception e) {
                f = new Font(Font.SANS_SERIF, Font.PLAIN, size);
            }
            fonts.put(key, f);
        }
        return f;
    }

    @Override public void fill(int x, int y, int w, int h, int argb)
    {
        g.setColor(new Color(argb, true));
        g.fillRect(x, y, w, h);
    }

    @Override public void image(String file, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, String fx, int alpha)
    {
        BufferedImage img = load(file, fx);
        if (img != null) g.drawImage(img, dx, dy, dx + dw, dy + dh, sx, sy, sx + sw, sy + sh, null);
    }

    @Override public int[] imageSize(String file)
    {
        BufferedImage img = load(file, "");
        return img == null ? null : new int[] { img.getWidth(), img.getHeight() };
    }

    @Override public void pixels(String key, int[] argb, int w, int h, int dx, int dy, int scale, int alpha)
    {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        g.drawImage(img, dx, dy, w * scale, h * scale, null);
    }

    @Override public float textWidth(String s, int size, int font)
    {
        return (float) font(size, font).getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    @Override public void text(String s, int x, int baseline, int size, int font, int argb)
    {
        g.setFont(font(size, font));
        g.setColor(new Color(argb, true));
        g.drawString(s, x, baseline);
    }

    @Override public void clip(int x, int y, int w, int h)
    {
        savedClip = g.getClip();
        g.setClip(x, y, w, h);
    }

    @Override public void unclip()
    {
        g.setClip(savedClip);
    }

    public static void main(String[] args) throws Exception
    {
        try (FileInputStream in = new FileInputStream(args[0])) {
            DsArt.loadFonts(in);
        }
        String json = new String(Files.readAllBytes(new File(args[1]).toPath()), StandardCharsets.UTF_8);
        DsScene scene = new DsScene();
        scene.setState(DsJson.map(DsJson.parse(json)));
        Preview p = new Preview(scene);
        if (args.length > 5) {                       // simulate a finger held at x,y
            scene.touchDown(Integer.parseInt(args[3]), Integer.parseInt(args[4]));
            if (args.length > 6) scene.touchMove(Integer.parseInt(args[5]), Integer.parseInt(args[6]));
        }
        scene.draw(p);
        ImageIO.write(p.out, "png", new File(args[2]));
    }
}
