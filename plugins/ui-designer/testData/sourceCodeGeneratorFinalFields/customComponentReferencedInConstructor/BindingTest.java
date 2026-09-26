import javax.swing.*;

public class BindingTest {
  private int a;
  public JComponent myRootComponent;
  private JLabel myLabel;

  public BindingTest() {
    a = 0;
    myLabel.setText("My Text");
  }

  private void createUIComponents() {
    myLabel = new JLabel();
  }
}
