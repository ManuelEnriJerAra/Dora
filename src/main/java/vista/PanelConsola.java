package vista;

import controlador.GestorConfiguracion;
import controlador.GestorServidores;
import controlador.AppErrorReporter;
import modelo.Server;
import com.formdev.flatlaf.extras.components.FlatCheckBox;
import com.formdev.flatlaf.extras.components.FlatScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PanelConsola extends JPanel {
    private static final Pattern CHAT = Pattern.compile("<([^>]+)>\\s*(.*)");
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern LEFT = Pattern.compile("([^\\s]+) left the game");
    private static final Pattern HORA = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");

    // Colores por tipo
    private static final Color COLOR_INFO = AppTheme.getConsoleInfoForeground();
    private static final Color COLOR_CHAT = AppTheme.getConsoleChatForeground();
    private static final Color COLOR_ERROR = AppTheme.getConsoleErrorForeground();

    private final int MAX_LINEAS = 5000;
    private static final int MAX_LINEAS_POR_DRENAJE = 500;
    private static final int MAX_HISTORIAL_COMANDOS = 100;
    // Margen en píxeles para considerar que el usuario está al final de la consola.
    private static final int MARGEN_AUTOSCROLL_PX = 24;

    // Siempre ajusta al ancho visible: una sola palabra larga (una ruta, por ejemplo) hacía que
    // el JTextPane dejase de partir líneas y todo el log se cortaba por la derecha.
    private final JTextPane consolaPane = new JTextPane() {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    };
    private JTextPane comandoPane = new JTextPane();
    private final StyledDocument documento = consolaPane.getStyledDocument();

    private Style styleInfo;
    private Style styleChat;
    private Style styleError;

    private final Deque<String> rawLineas = new ArrayDeque<>();
    private final Deque<String> pendingLineas = new ArrayDeque<>();
    private final Set<String> jugadoresConectados = new LinkedHashSet<>();
    private boolean drenajeConsolaProgramado;
    private int lineasRenderizadas;
    private final HistorialComandos historialComandos = new HistorialComandos(MAX_HISTORIAL_COMANDOS);
    private JScrollBar barraVertical;

    private final GestorServidores gestorServidores;
    private final FlatCheckBox vistaSimpleCheckbox = new FlatCheckBox();

    private int arc;
    private RoundedBackgroundPanel scrollWrap;
    private RoundedBackgroundPanel comandoWrap;

    public PanelConsola(GestorServidores gestorServidores) {
        this.setLayout(new BorderLayout(0, 8));
        this.setOpaque(false);
        this.gestorServidores = gestorServidores;
        this.setMinimumSize(new Dimension(this.getWidth(), 200));
        consolaPane.setEditable(false);
        // El desplazamiento lo controla appendLineas: solo se baja al final si el usuario ya estaba ahí.
        if (consolaPane.getCaret() instanceof javax.swing.text.DefaultCaret caret) {
            caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        }
        this.setBorder(BorderFactory.createEmptyBorder());
        refreshThemeRefs();

        // Inicialización de estilo
        consolaPane.setBackground(AppTheme.getConsoleBackground());
        consolaPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consolaPane.setOpaque(true);
        inicializarEstilo();

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setViewportView(consolaPane);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setWheelScrollingEnabled(true);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getConsoleBackground());
        barraVertical = scroll.getVerticalScrollBar();
        barraVertical.setUnitIncrement(16);

        vistaSimpleCheckbox.setText("Vista simple");
        vistaSimpleCheckbox.setToolTipText("Muestra solo arranques, jugadores, chat y errores");
        vistaSimpleCheckbox.setSelected(GestorConfiguracion.isConsolaVistaSimple());
        vistaSimpleCheckbox.addActionListener(e -> {
            GestorConfiguracion.guardarConsolaVistaSimple(vistaSimpleCheckbox.isSelected());
            actualizarConsola();
        });
        vistaSimpleCheckbox.setOpaque(false);
        vistaSimpleCheckbox.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        vistaSimpleCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
        vistaSimpleCheckbox.setForeground(AppTheme.getConsoleForeground());

        scrollWrap = new RoundedBackgroundPanel(AppTheme.getConsoleBackground(), arc);
        scrollWrap.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scrollWrap.add(scroll, BorderLayout.CENTER);

        JPanel overlayBadge = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppTheme.withAlpha(AppTheme.getSelectionBackground(), 110));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        overlayBadge.setOpaque(false);
        overlayBadge.add(vistaSimpleCheckbox, BorderLayout.CENTER);

        this.add(scrollWrap, BorderLayout.CENTER);

        JPanel panelComandos = new JPanel(new BorderLayout(8, 0));
        panelComandos.setBackground(AppTheme.getConsoleBackground());
        panelComandos.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JLabel pico = new JLabel(">");
        pico.setForeground(AppTheme.getConsoleForeground());
        pico.setFont(new Font("Monospaced", Font.PLAIN, 12));
        comandoPane.setEditable(true);
        comandoPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        comandoPane.setOpaque(false);
        comandoPane.setForeground(AppTheme.getConsoleForeground());
        comandoPane.setBorder(null);
        panelComandos.add(pico, BorderLayout.WEST);
        panelComandos.add(comandoPane, BorderLayout.CENTER);
        // La opción de vista va junto a la línea de comandos para no tapar el texto del log.
        panelComandos.add(overlayBadge, BorderLayout.EAST);
        // Interacción con la línea de comandos
        comandoPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "enviarComando");
        comandoPane.getActionMap().put("enviarComando", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String comando = comandoPane.getText().trim();
                if(comando.isBlank()) return;
                Server server = gestorServidores.getServidorSeleccionado();
                gestorServidores.mandarComando(server, comando);
                historialComandos.registrar(comando);
                comandoPane.setText("");
                irAlFinal();
            }
        });
        // Flechas arriba/abajo: recorrer los comandos enviados, como en una terminal.
        comandoPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "comandoAnterior");
        comandoPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "comandoSiguiente");
        comandoPane.getActionMap().put("comandoAnterior", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String anterior = historialComandos.anterior(comandoPane.getText());
                if(anterior != null) comandoPane.setText(anterior);
            }
        });
        comandoPane.getActionMap().put("comandoSiguiente", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String siguiente = historialComandos.siguiente();
                if(siguiente != null) comandoPane.setText(siguiente);
            }
        });
        comandoWrap = new RoundedBackgroundPanel(AppTheme.getConsoleBackground(), arc);
        comandoWrap.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        comandoWrap.add(panelComandos, BorderLayout.CENTER);

        this.add(comandoWrap, BorderLayout.SOUTH);

    }

    @Override
    public void updateUI(){
        super.updateUI();
        refreshThemeRefs();
        if(scrollWrap != null) scrollWrap.setArc(arc);
        if(comandoWrap != null) comandoWrap.setArc(arc);
    }

    private void refreshThemeRefs(){
        arc = AppTheme.getArc();
        Color consoleBackground = AppTheme.getConsoleBackground();
        if (consolaPane != null) {
            consolaPane.setBackground(consoleBackground);
        }
        if (scrollWrap != null) {
            scrollWrap.setBackground(consoleBackground);
        }
        if (comandoWrap != null) {
            comandoWrap.setBackground(consoleBackground);
        }
    }

    private void inicializarEstilo() {
        styleInfo = documento.addStyle("INFO", null);
        StyleConstants.setForeground(styleInfo, COLOR_INFO);

        styleChat = documento.addStyle("CHAT", null);
        StyleConstants.setForeground(styleChat, COLOR_CHAT);

        styleError = documento.addStyle("ERROR", null);
        StyleConstants.setForeground(styleError, COLOR_ERROR);
    }

    void actualizarConsola(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null) return;

        synchronized (pendingLineas) {
            pendingLineas.clear();
            drenajeConsolaProgramado = false;
        }
        rawLineas.clear();
        rawLineas.addAll(server.getRawLogLines());
        jugadoresConectados.clear();

        reconstruirDocumentoCompleto();
    }

    public void escribirLinea(String raw){
        if(raw == null || raw.isBlank()) return;

        synchronized (pendingLineas) {
            pendingLineas.addLast(raw);
            if(drenajeConsolaProgramado) {
                return;
            }
            drenajeConsolaProgramado = true;
        }
        SwingUtilities.invokeLater(this::drenarLineasPendientes);
    }

    private void drenarLineasPendientes() {
        int procesadas = 0;
        List<RenderLine> lote = new ArrayList<>();
        try {
            while(procesadas < MAX_LINEAS_POR_DRENAJE) {
                String raw;
                synchronized (pendingLineas) {
                    raw = pendingLineas.pollFirst();
                }
                if(raw == null) break;
                RenderLine linea = procesarLineaEnEdt(raw);
                if(linea != null) lote.add(linea);
                procesadas++;
            }
            appendLineas(lote);
        } catch (RuntimeException ex) {
            AppErrorReporter.report("No se ha podido pintar una línea de consola.", ex);
        }
        synchronized (pendingLineas) {
            if(pendingLineas.isEmpty()) {
                drenajeConsolaProgramado = false;
            } else {
                SwingUtilities.invokeLater(this::drenarLineasPendientes);
            }
        }
    }

    private RenderLine procesarLineaEnEdt(String raw) {
        rawLineas.addLast(raw);
        while(rawLineas.size()>MAX_LINEAS){
            rawLineas.removeFirst();
        }

        boolean vistaSimple = vistaSimpleCheckbox.isSelected();
        RenderLine renderLine = render(raw, vistaSimple);
        actualizarJugadoresConectados(raw);

        if(renderLine.texto == null || renderLine.texto.isEmpty()) return null;
        return renderLine;
    }

    private void reconstruirDocumentoCompleto(){
        try{
            documento.remove(0, documento.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        boolean vistaSimple = vistaSimpleCheckbox.isSelected();
        jugadoresConectados.clear();
        lineasRenderizadas = 0;
        List<RenderLine> lote = new ArrayList<>(rawLineas.size());
        for(String raw : rawLineas){
            RenderLine renderLine = render(raw, vistaSimple);
            actualizarJugadoresConectados(raw);
            if(renderLine.texto == null || renderLine.texto.isEmpty()) continue;
            lote.add(renderLine);
        }
        appendLineas(lote);
        irAlFinal();
    }

    // Inserta un lote de líneas con una inserción por tramo del mismo estilo y un solo desplazamiento.
    // Insertar línea a línea y mover el cursor en cada una hacía que la consola se quedase atrás.
    private void appendLineas(List<RenderLine> lote){
        if (lote == null || lote.isEmpty()) return;
        boolean estabaAlFinal = estaAlFinal();
        try{
            StringBuilder tramo = new StringBuilder();
            Style estiloTramo = null;
            for (RenderLine linea : lote) {
                if (estiloTramo != null && linea.estilo != estiloTramo) {
                    documento.insertString(documento.getLength(), tramo.toString(), estiloTramo);
                    tramo.setLength(0);
                }
                estiloTramo = linea.estilo;
                tramo.append(linea.texto).append('\n');
            }
            if (!tramo.isEmpty()) {
                documento.insertString(documento.getLength(), tramo.toString(), estiloTramo);
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        lineasRenderizadas += lote.size();
        recortarLineasRenderizadas();
        if (estabaAlFinal) {
            irAlFinal();
        }
    }

    private boolean estaAlFinal(){
        if (barraVertical == null || !barraVertical.isShowing()) return true;
        return debeSeguirAlFinal(barraVertical.getValue(), barraVertical.getVisibleAmount(), barraVertical.getMaximum());
    }

    static boolean debeSeguirAlFinal(int valor, int visible, int maximo){
        return valor + visible >= maximo - MARGEN_AUTOSCROLL_PX;
    }

    private void irAlFinal(){
        // Tras insertar, el tamaño del documento se recalcula en el siguiente layout.
        SwingUtilities.invokeLater(() -> {
            if (barraVertical != null) {
                barraVertical.setValue(barraVertical.getMaximum());
            }
        });
    }

    private void recortarLineasRenderizadas() {
        if(lineasRenderizadas <= MAX_LINEAS) return;
        try {
            Element root = documento.getDefaultRootElement();
            int sobrantes = Math.min(lineasRenderizadas - MAX_LINEAS, root.getElementCount() - 1);
            if(sobrantes <= 0) {
                return;
            }
            // Un solo remove para todas las líneas sobrantes.
            int fin = root.getElement(sobrantes - 1).getEndOffset();
            documento.remove(0, fin);
            lineasRenderizadas -= sobrantes;
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private RenderLine render(String raw, boolean vistaSimple){
        if(vistaSimple){
            String traducida = traducirLinea(raw); // puede devolver null
            if(traducida == null) return new RenderLine(null, styleInfo); // no se muestra
            return new RenderLine(traducida, estiloPorTexto(traducida));
        } else {
            return new RenderLine(raw, styleInfo);
        }
    }

    private Style estiloPorTexto(String texto){
        // Errores
        if (texto.contains("[ERROR]")) return styleError;

        // chat
        if (texto.contains("[CHAT]")) return styleChat;

        // info
        return  styleInfo;
    }

    private String traducirLinea(String linea){
        // ===== TRADUCCIÓN DE EVENTOS CONOCIDOS =====
        // vamos a quitarle la hora primero
        String hora = extraerHora(linea);


        if (linea.contains("[INFO]") || linea.contains("[CHAT]") || linea.contains("[ERROR]")) {
            return linea; // es una línea que yo he escrito, que pase
        }

        // Errores
        if (linea.contains("FAILED TO BIND TO PORT")){
            return hora+"[ERROR] El puerto ya está en uso, es probable que haya otro servidor abierto.";
        }

        if(linea.contains("Done")){
            return(hora+"[INFO] El servidor se ha iniciado exitosamente.");
        }
        if(linea.contains("All dimensions are saved")){
            return(hora+"[INFO] Mundo guardado.");
        }

        // Chat
        Matcher mChat = linea.indexOf('<') >= 0 ? CHAT.matcher(linea) : null;
        if(mChat != null && mChat.find()){
            String user = mChat.group(1);
            if(user != null) user = user.trim();
            if(user == null || user.isBlank() || !jugadoresConectados.contains(user)) return null;
            String mensaje = mChat.group(2);
            mensaje = mensaje.replaceAll("§.", "").trim();
            if(mensaje.isBlank()) return null;
            return (hora+"[CHAT] " + user + " ha dicho: " + mensaje);
        }

        // Join / Leave
        // contains() descarta casi todas las líneas sin pasar por la expresión regular, que es lenta
        // porque prueba cada posición de la línea.
        Matcher mJoin = linea.contains(" joined the game") ? JOIN.matcher(linea) : null;
        if(mJoin != null && mJoin.find()){
            return(hora+"[INFO] "+mJoin.group(1)+" ha entrado.");
        }
        Matcher mLeft = linea.contains(" left the game") ? LEFT.matcher(linea) : null;
        if(mLeft != null && mLeft.find()){
            return (hora+"[INFO] "+mLeft.group(1)+" ha salido.");
        }
        return null;
    }

    private void actualizarJugadoresConectados(String linea){
        if(linea == null || linea.isBlank()) return;

        Matcher mJoin = linea.contains(" joined the game") ? JOIN.matcher(linea) : null;
        if(mJoin != null && mJoin.find()){
            String nombre = mJoin.group(1);
            if(nombre != null){
                nombre = nombre.trim();
                if(!nombre.isBlank()){
                    jugadoresConectados.add(nombre);
                }
            }
            return;
        }

        Matcher mLeft = linea.contains(" left the game") ? LEFT.matcher(linea) : null;
        if(mLeft != null && mLeft.find()){
            String nombre = mLeft.group(1);
            if(nombre != null){
                nombre = nombre.trim();
                if(!nombre.isBlank()){
                    jugadoresConectados.remove(nombre);
                }
            }
        }
    }
    private static class RenderLine {
        final String texto;
        final Style estilo;
        RenderLine(String texto, Style estilo) {
            this.texto = texto;
            this.estilo = estilo;
        }
    }

    private String extraerHora(String linea){
        if(linea == null) return null;
        Matcher mHora = HORA.matcher(linea);
        if(mHora.find()){
            return "["+mHora.group(1)+"] ";
        }
        return null;
    }

    // Historial de comandos enviados, navegable con las flechas como en una terminal.
    static final class HistorialComandos {
        private final int maximo;
        private final List<String> comandos = new ArrayList<>();
        private int posicion = 0;
        private String borrador = "";

        HistorialComandos(int maximo) {
            this.maximo = Math.max(1, maximo);
        }

        void registrar(String comando) {
            if (comando == null || comando.isBlank()) return;
            if (comandos.isEmpty() || !comandos.get(comandos.size() - 1).equals(comando)) {
                comandos.add(comando);
                if (comandos.size() > maximo) {
                    comandos.remove(0);
                }
            }
            posicion = comandos.size();
            borrador = "";
        }

        String anterior(String textoActual) {
            if (comandos.isEmpty() || posicion == 0) return null;
            if (posicion == comandos.size()) {
                borrador = textoActual == null ? "" : textoActual;
            }
            posicion--;
            return comandos.get(posicion);
        }

        String siguiente() {
            if (posicion >= comandos.size()) return null;
            posicion++;
            return posicion == comandos.size() ? borrador : comandos.get(posicion);
        }
    }
}
