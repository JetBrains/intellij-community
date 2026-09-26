import javax.swing.*;

public class BindingTest {
  public JComponent myRootComponent;
  private JLabel myLabel;

  public BindingTest() {
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
