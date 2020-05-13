import javax.swing.*;
import java.awt.*;

public class GotItPanel {
  public JComponent myRootComponent;
  public JPanel myButton;
  public JLabel myTitle;
  public JLabel myButtonLabel;
  public JEditorPane myMessage;

  private void createUIComponents() {
    myButton = new JPanel();
    myMessage = new JEditorPane();    
  }
}
