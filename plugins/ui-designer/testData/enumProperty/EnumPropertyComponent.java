import javax.swing.*;

/**
 * A custom component with an enum-typed bean property, the case that goes through
 * {@code LwIntroEnumProperty} / {@code IntroEnumProperty}.
 */
public class EnumPropertyComponent extends JPanel {
  private Alignment alignment = Alignment.LEFT;

  public Alignment getAlignment() {
    return alignment;
  }

  public void setAlignment(Alignment alignment) {
    this.alignment = alignment;
  }
}
