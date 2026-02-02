import javax.swing.*;

public class BindingTest {
  public JComponent myRootComponent;
  private JLabel myLabel;
  private String myProject;
  private String myProjectTitle;
  private int x;

  public BindingTest(String project) {
    x = 2;
    super();
    myProject = project;
    myProjectTitle = project.toString();
    myLabel.setText("My Text");
  }

  private void createUIComponents() {
    myLabel = new JLabel();
  }
}
