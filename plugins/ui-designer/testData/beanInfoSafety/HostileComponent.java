import javax.swing.*;

/**
 * Component class used by AsmClassPropertiesProviderTest. Its static initializer and constructor
 * record that they ran, so a test can prove that reading a form never executes component code.
 */
public class HostileComponent extends JPanel {
  static {
    Payload.record("static-initializer");
  }

  private String label = "";

  public HostileComponent() {
    Payload.record("constructor");
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }
}
