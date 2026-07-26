package rich.screens.clickgui.dropdown.theme;

// отвечает за сохранение темы в табе
/** A named, saved color set. */
public class SavedTheme {

    public final String name;
    public final GuiTheme colors;

    public SavedTheme(String name, GuiTheme colors) {
        this.name = name;
        this.colors = colors;
    }
}
