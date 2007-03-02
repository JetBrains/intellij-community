import javax.swing.*;
import java.awt.*;

public class ConditionalMethodCallTest extends JPanel {
  public JComponent myRootComponent;

  public ConditionalMethodCallTest() {
    this(1);
  }

  public ConditionalMethodCallTest(int i) {
    setLayout(i == 0 ? new GridBagLayout() : new BorderLayout());
    myRootComponent.getComponentCount();
  }
}