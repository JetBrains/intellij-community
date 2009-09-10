import javax.swing.*;
import java.awt.*;

public class MethodCallInSuperTest extends JDialog {
  private JComponent myRootComponent;

  public MethodCallInSuperTest() {
    super(JOptionPane.getRootFrame(), "", true);
    getContentPane().add(myRootComponent);
  }
}