package rich.screens.clickgui.dropdown.theme;

import java.awt.Color;


// являеться примером для оформления одной темы

/** The editable color set that drives the whole menu. */
public class GuiTheme {

    public Color accent     = new Color(123, 112, 255);
    public Color background = new Color(16, 16, 20, 235); // panel fill / glass tint (alpha unused, see opacity)
    public Color text       = new Color(236, 236, 242);   // titles / active text
    public Color outline    = new Color(255, 255, 255, 26);// panel & control outlines
    public Color extra      = new Color(255, 255, 255, 22);// separators / control fills

    /** Panel transparency (0 = fully see-through, 1 = opaque). Drives the glass tint alpha. */
    public float opacity    = 235f / 255f;

    /** Liquid-glass (Rockstar-style) knobs. */
    public float glassStrength   = 0.25f;   // 0..1  edge fresnel highlight
    public float glassDistortion = 0.08f;   // -0.2..0.2 edge refraction (UV)
    public float glassBlur       = 3.0f;    // 0..8  Kawase blur strength (offset)

    public GuiTheme copy() {
        GuiTheme g = new GuiTheme();
        g.copyFrom(this);
        return g;
    }

    public void copyFrom(GuiTheme o) {
        accent = o.accent;
        background = o.background;
        text = o.text;
        outline = o.outline;
        extra = o.extra;
        opacity = o.opacity;
        glassStrength = o.glassStrength;
        glassDistortion = o.glassDistortion;
        glassBlur = o.glassBlur;
    }
}
