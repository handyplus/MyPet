import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Generates de/Keyle/MyPet/class-index.txt (read by BuildIntegrity) from class directories and jars.
 * <p>
 * Usage: {@code java build-tools/ClassIndex.java <output-file> <dir-or-jar> [<dir-or-jar> ...]}
 * <p>
 * Every entry that starts with {@code de/Keyle/MyPet/} and ends with {@code .class} is collected,
 * sorted, de-duplicated and written one per line (trailing newline included).
 */
public class ClassIndex {

    /**
     * Entry point.
     *
     * @param args output file followed by one or more class directories or jars
     * @throws IOException when an input cannot be read or the output cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: ClassIndex.java <output-file> <dir-or-jar> [<dir-or-jar> ...]");
            System.exit(1);
        }
        Set<String> names = new TreeSet<>();
        for (int i = 1; i < args.length; i++) {
            Path input = Paths.get(args[i]);
            if (!Files.exists(input)) {
                continue;
            }
            if (Files.isDirectory(input)) {
                collectFromDir(input, names);
            } else {
                collectFromJar(input, names);
            }
        }
        StringBuilder content = new StringBuilder();
        for (String name : names) {
            content.append(name).append('\n');
        }
        Path output = Paths.get(args[0]);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.write(output, content.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("class-index: " + names.size() + " entries -> " + output);
    }

    /**
     * Whether a jar/directory entry is a MyPet class.
     *
     * @param entryName entry name using '/' separators
     * @return true for MyPet class files
     */
    private static boolean isMyPetClass(String entryName) {
        return entryName.startsWith("de/Keyle/MyPet/") && entryName.endsWith(".class");
    }

    /**
     * Collects MyPet class names from a class directory.
     *
     * @param root  class directory
     * @param names target set
     * @throws IOException when the directory cannot be walked
     */
    private static void collectFromDir(Path root, Set<String> names) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                if (isMyPetClass(rel)) {
                    names.add(rel);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Collects MyPet class names from a jar.
     *
     * @param jar   jar file
     * @param names target set
     * @throws IOException when the jar cannot be read
     */
    private static void collectFromJar(Path jar, Set<String> names) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(jar)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (isMyPetClass(entry.getName())) {
                    names.add(entry.getName());
                }
                zis.closeEntry();
            }
        }
    }
}
