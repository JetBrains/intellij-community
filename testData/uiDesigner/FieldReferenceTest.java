import javax.swing.*;
import java.awt.*;

public class FieldReferenceTest extends JPanel {
  private JComponent myRootComponent;

  public FieldReferenceTest() {
    super(new BorderLayout());
    add(myRootComponent, BorderLayout.CENTER);
  }
}