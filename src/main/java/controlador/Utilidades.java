/*
 * Fichero: Utilidades.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase contiene diferentes métodos que utilizan otras clases. Son métodos varios como copiar un archivo o extraer
 * un porcentaje de una línea de texto.
 * */

package controlador;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Utilidades {
    private static final char SECTION_SIGN = '\u00A7';
    private static final Pattern ANSI_ESCAPE_SEQUENCE = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]|\\u009B[0-?]*[ -/]*[@-~]");
    private static final Pattern ORPHAN_ANSI_COLOR_SEQUENCE = Pattern.compile("\\[(?:\\d{1,3};)*\\d{1,3}m");

    public static void copiarArchivo(File origen, File destino){
        try{
            InputStream in = new FileInputStream(origen);
            OutputStream out = new FileOutputStream(destino);

            byte[] buffer = new byte[1024]; // creamos un buffer para leer de 1KB en 1KB
            int length;
            while((length = in.read(buffer))>0){
                out.write(buffer, 0, length);
            }
            in.close();
            out.close();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        System.out.println("Archivo copiado a: "+ destino.getAbsolutePath());
    }

    public static void copiarDirectorio(Path origen, Path destino) throws IOException {
        FileSystemSafety.copyDirectoryTree(origen, destino);
    }

    public static void eliminarDirectorio(Path directorio) throws IOException {
        FileSystemSafety.deleteDirectoryTree(directorio);
    }

    public static void moverDirectorio(Path origen, Path destino) throws IOException {
        if(origen == null || destino == null) throw new IllegalArgumentException("Las rutas no pueden ser nulas.");
        if(!Files.isDirectory(origen)) throw new IllegalArgumentException("El origen no es un directorio: " + origen);

        try{
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex){
            copiarDirectorio(origen, destino);
            eliminarDirectorio(origen);
        }
    }
    // genera un archivo llamado eula.txt con el texto eula=true en la dirección indicada
    public static void rellenaEULA(File direccion){
        File eula = new File(direccion,"eula.txt");
        try{
            FileWriter fw = new FileWriter(eula);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("eula=true");
            bw.close();
            fw.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static ImageIcon escalarIcono(ImageIcon icono, int ancho, int alto){
        Image img = icono.getImage();

        BufferedImage bi = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(img, 0, 0, ancho, alto, null);
        g2d.dispose();

        return new ImageIcon(bi);
    }

    // Esta función encuentra un archivo jar a partir de un servidor. Si lo encuentra devuelve el nombre, si no NULL
    public static Path encontrarEjecutableJar(Path direccion){
        try (Stream<Path> archivos = Files.list(direccion)){
            // filtramos y nos quedamos solo con los archivos jar
            List<Path> jars = archivos.filter(path -> path.toString().toLowerCase().endsWith(".jar")).toList();
            if(jars.isEmpty()){ // vacío
                throw new IllegalStateException("No hay ningún jar en la dirección "+direccion);
            }
            if(jars.size()>1){ // más de un jar
                throw new IllegalStateException("Se ha encontrado más de un jar en la dirección "+direccion);
            }
            return jars.get(0);
        } catch (IOException e){
            System.out.println("No se ha podido encontrar el jar: "+e.getMessage());
            return null;
        }
    }

    public static int extraerPorcentaje(String line) {
        int indicePorcentaje = line.indexOf("%");

        if (indicePorcentaje == -1) return 0; // por seguridad

        // Tomamos la parte izquierda del %
        String izquierda = line.substring(0, indicePorcentaje);

        // Ahora nos quedamos solo con lo que hay después del último espacio
        String numero = izquierda.substring(
                izquierda.lastIndexOf(" ") + 1
        );

        return Integer.parseInt(numero);
    }

    public static String leerMotdDesdeProperties(Path direccion){
        if(direccion == null) return null;
        Path propertiesPath = direccion.resolve("server.properties");
        if(!Files.exists(propertiesPath)) return null;

        try{
            Properties properties = cargarPropertiesUtf8(propertiesPath);
            return normalizarTextoMojibakeUtf8(properties.getProperty("motd"));
        } catch (IOException e){
            return null;
        }
    }

    public static void escribirPuertoEnProperties(Path direccion, int puerto){
        if(direccion == null) return;
        if(puerto <= 0 || puerto > 65535) return;

        Path propertiesPath = direccion.resolve("server.properties");
        try {
            Files.createDirectories(direccion);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Si el archivo existe pero no se puede leer NO seguimos: guardar aquí lo sustituiría
        // por uno casi vacío y se perdería toda la configuración del servidor.
        Properties properties;
        try{
            properties = cargarPropertiesUtf8(propertiesPath);
        } catch (IOException e){
            throw new RuntimeException("No se ha podido leer server.properties, no se modificará: " + e.getMessage(), e);
        }

        properties.setProperty("server-port", String.valueOf(puerto));

        try{
            guardarPropertiesUtf8(propertiesPath, properties, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void escribirMotdEnProperties(Path direccion, String motd){
        if(direccion == null) return;
        if(motd == null) motd = "";

        Path propertiesPath = direccion.resolve("server.properties");
        try {
            Files.createDirectories(direccion);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Si el archivo existe pero no se puede leer NO seguimos: guardar aquí lo sustituiría
        // por uno casi vacío y se perdería toda la configuración del servidor.
        Properties properties;
        try{
            properties = cargarPropertiesUtf8(propertiesPath);
        } catch (IOException e){
            throw new RuntimeException("No se ha podido leer server.properties, no se modificará: " + e.getMessage(), e);
        }

        properties.setProperty("motd", motd);

        try{
            guardarPropertiesUtf8(propertiesPath, properties, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String fromMStoDateString(Long ms){
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    public static File resolveSystemPicturesDirectory() {
        return resolveSystemPicturesDirectory(System.getProperty("os.name", ""), System.getProperty("user.home"), System.getenv());
    }

    static File resolveSystemPicturesDirectory(String osName, String userHome, Map<String, String> environment) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase();
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        if(normalizedOs.contains("win")) {
            addCandidate(candidates, readEnv(environment, "OneDrive"), "Pictures");
            addCandidate(candidates, readEnv(environment, "USERPROFILE"), "Pictures");
        } else {
            Path xdgPictures = resolveXdgPicturesDirectory(userHome, environment);
            if(xdgPictures != null) {
                candidates.add(xdgPictures);
            }
        }

        addCandidate(candidates, userHome, "Pictures");
        addCandidate(candidates, userHome, "Imagenes");
        addCandidate(candidates, userHome, "Imágenes");

        for(Path candidate : candidates) {
            if(candidate != null && Files.isDirectory(candidate)) {
                return candidate.toFile();
            }
        }

        File defaultDirectory = FileSystemView.getFileSystemView().getDefaultDirectory();
        if(defaultDirectory != null && defaultDirectory.isDirectory()) {
            return defaultDirectory;
        }

        if(userHome != null && !userHome.isBlank()) {
            File home = Path.of(userHome).toFile();
            if(home.isDirectory()) {
                return home;
            }
        }
        return null;
    }

    private static void addCandidate(LinkedHashSet<Path> candidates, String basePath, String child) {
        if(basePath == null || basePath.isBlank()) {
            return;
        }
        Path base = Path.of(basePath);
        candidates.add(child == null || child.isBlank() ? base : base.resolve(child));
    }

    private static String readEnv(Map<String, String> environment, String key) {
        if(environment == null || key == null || key.isBlank()) {
            return null;
        }
        return environment.get(key);
    }

    private static Path resolveXdgPicturesDirectory(String userHome, Map<String, String> environment) {
        if(userHome == null || userHome.isBlank()) {
            return null;
        }
        String xdgConfigHome = readEnv(environment, "XDG_CONFIG_HOME");
        Path configFile = xdgConfigHome == null || xdgConfigHome.isBlank()
                ? Path.of(userHome, ".config", "user-dirs.dirs")
                : Path.of(xdgConfigHome, "user-dirs.dirs");
        if(!Files.isRegularFile(configFile)) {
            return null;
        }

        try {
            for(String line : Files.readAllLines(configFile)) {
                if(line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if(!trimmed.startsWith("XDG_PICTURES_DIR=")) {
                    continue;
                }
                String value = trimmed.substring("XDG_PICTURES_DIR=".length()).trim();
                if(value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                value = value.replace("$HOME", userHome);
                if(!value.isBlank()) {
                    return Path.of(value);
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static Properties cargarPropertiesUtf8(Path propertiesPath) throws IOException {
        Properties properties = new Properties();
        if(propertiesPath == null || !Files.isRegularFile(propertiesPath)) {
            return properties;
        }
        byte[] bytes = Files.readAllBytes(propertiesPath);
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            // Archivos editados a mano en ANSI/Latin-1 (p. ej. un MOTD con tildes guardado desde el Bloc de notas).
            content = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        if(content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        try(Reader reader = new StringReader(content)){
            properties.load(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException("El archivo " + propertiesPath.getFileName() + " contiene secuencias no válidas.", e);
        }
        return properties;
    }

    public static void guardarPropertiesUtf8(Path propertiesPath, Properties properties, String comments) throws IOException {
        if(propertiesPath == null) {
            throw new IOException("La ruta de properties no es válida.");
        }
        StringWriter buffer = new StringWriter();
        (properties == null ? new Properties() : properties).store(buffer, comments);
        escribirArchivoAtomico(propertiesPath, buffer.toString().getBytes(StandardCharsets.UTF_8));
    }

    // Copia un archivo de datos ilegible a un nombre único para que no se pierda al volver a guardar.
    static Path respaldarArchivoIlegible(Path archivo) {
        if (archivo == null || !Files.isRegularFile(archivo)) {
            return null;
        }
        try {
            String marca = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path copia = archivo.resolveSibling(archivo.getFileName() + ".corrupt-" + marca);
            int intento = 1;
            while (Files.exists(copia)) {
                copia = archivo.resolveSibling(archivo.getFileName() + ".corrupt-" + marca + "-" + intento++);
            }
            Files.copy(archivo, copia);
            return copia;
        } catch (IOException | RuntimeException ex) {
            System.err.println("No se ha podido respaldar " + archivo + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Escribe el contenido en un temporal junto al destino y lo mueve encima. Así un cierre inesperado
     * a mitad de escritura nunca deja el archivo real truncado.
     */
    public static void escribirArchivoAtomico(Path destino, byte[] contenido) throws IOException {
        if(destino == null) {
            throw new IOException("La ruta de destino no es válida.");
        }
        Path absoluto = destino.toAbsolutePath();
        Path parent = absoluto.getParent();
        if(parent != null) {
            Files.createDirectories(parent);
        }
        Path temporal = Files.createTempFile(parent, absoluto.getFileName().toString() + ".", ".tmp");
        try {
            Files.write(temporal, contenido == null ? new byte[0] : contenido);
            try {
                Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    public static String normalizarTextoMojibakeUtf8(String value) {
        if(value == null || value.isEmpty()) {
            return value;
        }

        String normalized = value.replace("\u00C2" + String.valueOf(SECTION_SIGN), String.valueOf(SECTION_SIGN));
        String repaired = intentarRepararUtf8MalInterpretado(normalized);
        return tieneMenosMojibake(repaired, normalized) ? repaired : normalized;
    }

    public static String limpiarSecuenciasConsola(String value) {
        if(value == null || value.isEmpty()) {
            return value;
        }

        String cleaned = ANSI_ESCAPE_SEQUENCE.matcher(value).replaceAll("");
        return ORPHAN_ANSI_COLOR_SEQUENCE.matcher(cleaned).replaceAll("");
    }

    private static String intentarRepararUtf8MalInterpretado(String value) {
        try{
            return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (RuntimeException ex){
            return value;
        }
    }

    private static boolean tieneMenosMojibake(String candidate, String original) {
        return puntuacionMojibake(candidate) < puntuacionMojibake(original);
    }

    private static int puntuacionMojibake(String value) {
        if(value == null || value.isEmpty()) {
            return 0;
        }
        int score = 0;
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if(c == '\u00C2' || c == '\u00C3' || c == '\uFFFD') {
                score++;
            }
        }
        return score;
    }

}

