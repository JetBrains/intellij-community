import com.intellij.ide.IdeTooltipManager;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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
