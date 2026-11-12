package controlador.extensions;

import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionUpdateState;

import java.util.List;

public record InstalledExtensionStatus(
        ExtensionCompatibilityReport compatibility,
        ExtensionInstallState installState,
        ExtensionUpdateState updateState,
        boolean filePresent,
        boolean updateAvailable,
        List<String> missingDependencies,
        List<String> warnings,
        List<String> problems
) {
    public InstalledExtensionStatus {
        compatibility = compatibility == null
                ? new ExtensionCompatibilityReport(ExtensionCompatibilityStatus.WARNING, "Estado desconocido.", List.of())
                : compatibility;
        installState = installState == null ? ExtensionInstallState.UNKNOWN : installState;
        updateState = updateState == null ? ExtensionUpdateState.UNKNOWN : updateState;
        missingDependencies = missingDependencies == null ? List.of() : List.copyOf(missingDependencies);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public ExtensionCompatibilityStatus severity() {
        if (!filePresent || installState == ExtensionInstallState.FAILED || compatibility.incompatible() || !problems.isEmpty()) {
            return ExtensionCompatibilityStatus.INCOMPATIBLE;
        }
        if (updateAvailable || !missingDependencies.isEmpty() || !warnings.isEmpty()
                || installState == ExtensionInstallState.UNKNOWN) {
            return ExtensionCompatibilityStatus.WARNING;
        }
        return ExtensionCompatibilityStatus.COMPATIBLE;
    }

    public String summary() {
        if (!filePresent) {
            return "Archivo no encontrado";
        }
        if (installState == ExtensionInstallState.FAILED) {
            return "Instalación fallida";
        }
        if (!problems.isEmpty()) {
            return problems.getFirst();
        }
        if (!missingDependencies.isEmpty()) {
            List<String> dependencyNames = missingDependencies.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
            if (missingDependencies.size() == 1) {
                return "Falta la dependencia " + dependencyNames.stream().findFirst().orElse("desconocida");
            }
            return "Faltan " + missingDependencies.size() + " dependencias: "
                    + (dependencyNames.isEmpty() ? "desconocidas" : String.join(", ", dependencyNames));
        }
        if (compatibility.incompatible()) {
            return compatibility.summary();
        }
        if (updateAvailable) {
            return "Actualización disponible";
        }
        return "Instalada correctamente";
    }

    public List<String> diagnostics() {
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>();
        diagnostics.addAll(problems);
        diagnostics.addAll(missingDependencies.stream().map(name -> "Dependencia requerida no instalada: " + name).toList());
        diagnostics.addAll(warnings);
        if (compatibility.reasons() != null) {
            diagnostics.addAll(compatibility.reasons());
        }
        return List.copyOf(diagnostics);
    }
}
