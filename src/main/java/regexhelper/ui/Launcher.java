package regexhelper.ui;

/**
 * Точка входа для запуска из IDE и из jar без настройки module path.
 * Класс не наследует Application, поэтому JavaFX на classpath запускается без ошибки
 * «JavaFX runtime components are missing».
 */
public final class Launcher {

    private Launcher() { }

    public static void main(String[] args) {
        RegexHelperApp.main(args);
    }
}