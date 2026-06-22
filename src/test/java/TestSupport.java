import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class TestSupport {

    private TestSupport() {
    }

    static Path fixture(String name) {
        try {
            return Paths.get(TestSupport.class.getResource("/fixtures/" + name).toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Missing test fixture: " + name, e);
        }
    }

    static String fixturePath(String name) {
        return fixture(name).toString();
    }

    static String readFixture(String name) {
        try {
            return Files.readString(fixture(name), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read fixture: " + name, e);
        }
    }

    static Path copyFixture(Path targetDir, String fixtureName, String targetName) throws Exception {
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(targetName);
        Files.copy(fixture(fixtureName), target);
        return target;
    }

    static Path writeZip(Path zipPath, String entryName, String content) throws Exception {
        Path parent = zipPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zipPath;
    }

    static String entryFromZipBytes(byte[] zipBytes, String entryName) throws Exception {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int read;
                    while ((read = zis.read(buf)) != -1) {
                        out.write(buf, 0, read);
                    }
                    return out.toString(StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    static byte[] zipBytes(String entryName, String content) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return buffer.toByteArray();
    }

    static void writeString(Path path, String content) throws Exception {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}

