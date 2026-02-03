import javax.swing.*;

public class BindingTest {
  public JComponent myRootComponent;
  private JLabel myLabel;

  public BindingTest() {
    this(0);
  }

  public BindingTest(int i) {
    doTest();
    myLabel.setText("My Text");
  }

  private void doTest() {
  }

  private void createUIComponents() {
    myLabel = new JLabel();
  }
}
