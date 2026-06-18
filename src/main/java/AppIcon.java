import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the application icon with Java2D, so the same vault design can be used for the live
 * JavaFX window icon ({@link #createIcons()}) and for exporting real PNG/ICO assets used by
 * native packaging (see {@code IconExporter}).
 *
 * <p>Using Java2D (instead of a JavaFX Canvas snapshot) avoids any dependency on the FX scene
 * graph or render timing, which makes the window/taskbar icon appear reliably.
 */
public final class AppIcon {

    /** Standard icon sizes; the OS picks the crispest variant per surface. */
    public static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() {
    }

    /** Builds the icon at every standard size for {@code stage.getIcons()}. */
    public static List<Image> createIcons() {
        List<Image> images = new ArrayList<>();
        for (int size : ICON_SIZES) {
            images.add(new Image(new ByteArrayInputStream(renderPng(size))));
        }
        return images;
    }

    /** Renders a single icon as PNG-encoded bytes. */
    public static byte[] renderPng(int size) {
        BufferedImage image = renderImage(size);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode application icon", e);
        }
        return out.toByteArray();
    }

    /** Renders a single icon as an ARGB {@link BufferedImage} of the given pixel size. */
    public static BufferedImage renderImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            draw(g, size);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void draw(Graphics2D g, double s) {
        double pad = s * 0.06;
        double inner = s - pad * 2;
        double radius = s * 0.22;

        // Rounded vault panel background (diagonal gradient).
        g.setPaint(new GradientPaint(
                (float) pad, (float) pad, rgb("#1b2a4a"),
                (float) (pad + inner), (float) (pad + inner), rgb("#0d1830")));
        RoundRectangle2D panel = new RoundRectangle2D.Double(pad, pad, inner, inner, radius * 2, radius * 2);
        g.fill(panel);

        // Accent border.
        g.setPaint(rgba("#4da3ff", 0.55));
        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.02)));
        g.draw(panel);

        double cx = s / 2.0;
        double cy = s / 2.0;
        double r = s * 0.30;

        // Vault dial (vertical gradient).
        g.setPaint(new GradientPaint(
                0f, (float) (cy - r), rgb("#5fb0ff"),
                0f, (float) (cy + r), rgb("#2f6fd0")));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        // Inner ring.
        g.setPaint(rgba("#0d1830", 0.85));
        g.setStroke(new BasicStroke((float) Math.max(1, s * 0.03)));
        double ringR = r * 0.7;
        g.draw(new Ellipse2D.Double(cx - ringR, cy - ringR, ringR * 2, ringR * 2));

        // Locking bolts around the dial.
        g.setPaint(rgb("#eaf3ff"));
        int bolts = 8;
        double boltR = Math.max(0.5, s * 0.028);
        for (int i = 0; i < bolts; i++) {
            double angle = (Math.PI * 2 * i) / bolts;
            double bx = cx + Math.cos(angle) * r * 0.86;
            double by = cy + Math.sin(angle) * r * 0.86;
            g.fill(new Ellipse2D.Double(bx - boltR, by - boltR, boltR * 2, boltR * 2));
        }

        // Keyhole.
        g.setPaint(rgb("#0d1830"));
        double khR = s * 0.07;
        g.fill(new Ellipse2D.Double(cx - khR, cy - khR * 1.2, khR * 2, khR * 2));
        Path2D stem = new Path2D.Double();
        stem.moveTo(cx - khR * 0.55, cy);
        stem.lineTo(cx + khR * 0.55, cy);
        stem.lineTo(cx + khR * 1.05, cy + s * 0.13);
        stem.lineTo(cx - khR * 1.05, cy + s * 0.13);
        stem.closePath();
        g.fill(stem);
    }

    private static Color rgb(String hex) {
        return Color.decode(hex);
    }

    private static Color rgba(String hex, double alpha) {
        Color c = Color.decode(hex);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(alpha * 255));
    }
}

