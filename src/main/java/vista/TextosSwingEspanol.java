package vista;

import javax.swing.UIManager;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Textos de los diálogos estándar de Swing (JOptionPane y JFileChooser) en español.
 * Java elige estos textos según el idioma del sistema, así que en un Windows en inglés
 * los botones salían como "Yes", "No" o "Cancel" en medio de una interfaz en español.
 * No se cambia el Locale por defecto para no alterar el formato de números ni fechas.
 */
public final class TextosSwingEspanol {

    private TextosSwingEspanol() {
    }

    static final Map<String, String> TEXTOS = crearTextos();

    private static Map<String, String> crearTextos() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("OptionPane.yesButtonText", "Sí");
        t.put("OptionPane.noButtonText", "No");
        t.put("OptionPane.cancelButtonText", "Cancelar");
        t.put("OptionPane.okButtonText", "Aceptar");
        t.put("OptionPane.titleText", "Selecciona una opción");
        t.put("OptionPane.inputDialogTitle", "Entrada");
        t.put("OptionPane.messageDialogTitle", "Mensaje");

        t.put("FileChooser.lookInLabelText", "Buscar en:");
        t.put("FileChooser.saveInLabelText", "Guardar en:");
        t.put("FileChooser.fileNameLabelText", "Nombre:");
        t.put("FileChooser.folderNameLabelText", "Carpeta:");
        t.put("FileChooser.filesOfTypeLabelText", "Tipo:");
        t.put("FileChooser.openButtonText", "Abrir");
        t.put("FileChooser.saveButtonText", "Guardar");
        t.put("FileChooser.cancelButtonText", "Cancelar");
        t.put("FileChooser.updateButtonText", "Actualizar");
        t.put("FileChooser.helpButtonText", "Ayuda");
        t.put("FileChooser.directoryOpenButtonText", "Abrir");
        t.put("FileChooser.openDialogTitleText", "Abrir");
        t.put("FileChooser.saveDialogTitleText", "Guardar");
        t.put("FileChooser.acceptAllFileFilterText", "Todos los archivos");
        t.put("FileChooser.openButtonToolTipText", "Abrir el archivo seleccionado");
        t.put("FileChooser.saveButtonToolTipText", "Guardar el archivo seleccionado");
        t.put("FileChooser.cancelButtonToolTipText", "Cerrar sin elegir");
        t.put("FileChooser.directoryOpenButtonToolTipText", "Abrir la carpeta seleccionada");
        t.put("FileChooser.upFolderToolTipText", "Subir un nivel");
        t.put("FileChooser.homeFolderToolTipText", "Inicio");
        t.put("FileChooser.newFolderToolTipText", "Nueva carpeta");
        t.put("FileChooser.listViewButtonToolTipText", "Lista");
        t.put("FileChooser.detailsViewButtonToolTipText", "Detalles");
        t.put("FileChooser.viewMenuLabelText", "Ver");
        t.put("FileChooser.refreshActionLabelText", "Actualizar");
        t.put("FileChooser.newFolderActionLabelText", "Nueva carpeta");
        t.put("FileChooser.listViewActionLabelText", "Lista");
        t.put("FileChooser.detailsViewActionLabelText", "Detalles");
        t.put("FileChooser.fileNameHeaderText", "Nombre");
        t.put("FileChooser.fileSizeHeaderText", "Tamaño");
        t.put("FileChooser.fileTypeHeaderText", "Tipo");
        t.put("FileChooser.fileDateHeaderText", "Modificado");
        t.put("FileChooser.fileAttrHeaderText", "Atributos");
        t.put("FileChooser.newFolderErrorText", "No se ha podido crear la carpeta");
        t.put("FileChooser.newFolderName", "Nueva carpeta");
        t.put("FileChooser.win32.newFolder", "Nueva carpeta");
        t.put("FileChooser.other.newFolder", "Nueva carpeta");
        return t;
    }

    // Las teclas rápidas en inglés (Alt+Y para "Yes") no casan con los textos en español.
    private static final String[] MNEMONICOS = {
            "OptionPane.yesButtonMnemonic", "OptionPane.noButtonMnemonic",
            "OptionPane.cancelButtonMnemonic", "OptionPane.okButtonMnemonic",
            "FileChooser.openButtonMnemonic", "FileChooser.saveButtonMnemonic",
            "FileChooser.cancelButtonMnemonic", "FileChooser.updateButtonMnemonic",
            "FileChooser.helpButtonMnemonic", "FileChooser.directoryOpenButtonMnemonic",
            "FileChooser.lookInLabelMnemonic", "FileChooser.saveInLabelMnemonic",
            "FileChooser.fileNameLabelMnemonic", "FileChooser.folderNameLabelMnemonic",
            "FileChooser.filesOfTypeLabelMnemonic"
    };

    /** Se aplica sobre los valores del usuario de UIManager, que sobreviven a cambios de tema. */
    public static void aplicar() {
        TEXTOS.forEach(UIManager::put);
        for (String clave : MNEMONICOS) {
            UIManager.put(clave, "0");
        }
    }
}
