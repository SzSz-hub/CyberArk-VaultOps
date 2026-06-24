import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;


public final class IconExporter {

    // macOS .icns OSTypes that carry PNG payloads, paired with their pixel sizes (menu bar up to Retina dock).
    private static final String[] ICNS_TYPES = {"icp4", "icp5", "ic07", "ic08", "ic09", "ic10"};
    private static final int[] ICNS_SIZES = {16, 32, 128, 256, 512, 1024};

    private IconExporter() {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");

        Path outputDir = Paths.get(args.length > 0 ? args[0] : ".");
        Files.createDirectories(outputDir);

        List<byte[]> pngs = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (int size : AppIcon.ICON_SIZES) {
            byte[] png = AppIcon.renderPng(size);
            Files.write(outputDir.resolve("app-icon-" + size + ".png"), png);
            pngs.add(png);
            sizes.add(size);
        }

        Path icoPath = outputDir.resolve("app-icon.ico");
        writeIco(pngs, sizes, icoPath);
        System.out.println("Wrote " + icoPath.toAbsolutePath());

        Path icnsPath = outputDir.resolve("app-icon.icns");
        writeIcns(icnsPath);
        System.out.println("Wrote " + icnsPath.toAbsolutePath());

        if (args.length >= 4) {
            Path mainJar = Paths.get(args[1]);
            Path inputDir = Paths.get(args[2]);
            Path distDir = Paths.get(args[3]);

            deleteRecursively(distDir);
            deleteRecursively(inputDir);
            Files.createDirectories(inputDir);
            Files.copy(mainJar, inputDir.resolve(mainJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Prepared jpackage input at " + inputDir.toAbsolutePath());
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void writeIcns(Path target) throws IOException {
        // ICNS = "icns" magic + total length, then per-icon: 4-char OSType + length (incl. 8-byte header) + PNG.
        List<byte[]> elements = new ArrayList<>();
        int totalLength = 8;
        for (int i = 0; i < ICNS_TYPES.length; i++) {
            byte[] png = AppIcon.renderPng(ICNS_SIZES[i]);
            ByteBuffer element = ByteBuffer.allocate(8 + png.length).order(ByteOrder.BIG_ENDIAN);
            element.put(ICNS_TYPES[i].getBytes(StandardCharsets.US_ASCII));
            element.putInt(8 + png.length);
            element.put(png);
            elements.add(element.array());
            totalLength += 8 + png.length;
        }

        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(target))) {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
            header.put("icns".getBytes(StandardCharsets.US_ASCII));
            header.putInt(totalLength);
            os.write(header.array());
            for (byte[] element : elements) {
                os.write(element);
            }
        }
    }

    private static void writeIco(List<byte[]> pngs, List<Integer> sizes, Path target) throws IOException {        int count = pngs.size();
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(target))) {
            // ICONDIR header.
            ByteBuffer header = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
            header.putShort((short) 0); // reserved
            header.putShort((short) 1); // type 1 = icon
            header.putShort((short) count);
            os.write(header.array());

            // ICONDIRENTRY records.
            int offset = 6 + count * 16;
            for (int i = 0; i < count; i++) {
                int size = sizes.get(i);
                byte[] png = pngs.get(i);

                ByteBuffer entry = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                entry.put((byte) (size >= 256 ? 0 : size)); // width  (0 means 256)
                entry.put((byte) (size >= 256 ? 0 : size)); // height (0 means 256)
                entry.put((byte) 0);   // color count
                entry.put((byte) 0);   // reserved
                entry.putShort((short) 1);  // color planes
                entry.putShort((short) 32); // bits per pixel
                entry.putInt(png.length);   // size of image data
                entry.putInt(offset);       // offset of image data
                os.write(entry.array());

                offset += png.length;
            }

            // Image payloads.
            for (byte[] png : pngs) {
                os.write(png);
            }
        }
    }
}