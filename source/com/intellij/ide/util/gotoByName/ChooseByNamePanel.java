package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public class ChooseByNamePanel extends ChooseByNameBase {
  private JPanel myPanel;

  public ChooseByNamePanel(Project project, ChooseByNameModel model, String initialText){
    super(project, model, initialText);
  }

  protected void initUI(ChooseByNameBase.Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, modalityState, allowMultipleSelection);

    //myTextFieldPanel.setBorder(new EmptyBorder(0,0,0,0));
    myTextFieldPanel.setBorder(null);

    myPanel = new JPanel(new GridBagLayout());

    myPanel.add(myTextFieldPanel, new GridBagConstraints(0,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));
    myPanel.add(myListScrollPane, new GridBagConstraints(0,1,1,1,1,1,GridBagConstraints.WEST,GridBagConstraints.BOTH,new Insets(0,0,0,0),0,0));
  }

  public JComponent getPreferredFocusedComponent() {
    return myTextField;
  }

  protected void showList(){
  }

  protected void hideList(){
  }

  protected void close(boolean isOk) {
  }

  protected boolean isShowListForEmptyPattern() {
    return true;
  }

  protected boolean isCloseByFocusLost() {
    return false;
  }

  protected boolean isCheckboxVisible(){
    return false;
  }

  public JPanel getPanel(){
    return myPanel;
  }

}
