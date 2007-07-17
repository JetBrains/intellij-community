package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;

/**
 * @author yole
 */
public class RelocateDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myFromURLTextField;
  private JTextField myToURLTextField;

  public RelocateDialog(Project project, final SVNURL url) {
    super(project, false);
    init();
    setTitle("Relocate Working Copy");
    myFromURLTextField.setText(url.toString());
    myToURLTextField.setText(url.toString());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getBeforeURL() {
    return myFromURLTextField.getText();
  }

  public String getAfterURL() {
    return myToURLTextField.getText();
  }
}