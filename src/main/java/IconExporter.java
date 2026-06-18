import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;


public final class IconExporter {

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

        // Optional: prepare a clean, jar-only jpackage input dir and wipe the previous output so
        // re-running the native-package build never fails with "directory already exists".
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

    private static void writeIco(List<byte[]> pngs, List<Integer> sizes, Path target) throws IOException {
        int count = pngs.size();
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

