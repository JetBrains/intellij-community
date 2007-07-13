package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import java.awt.*;

public class ChooseByNamePanel extends ChooseByNameBase {
  private JPanel myPanel;
  private boolean myCheckBoxVisible = false;

  public ChooseByNamePanel(Project project, ChooseByNameModel model, String initialText, boolean isCheckboxVisible, final PsiElement context){
    super(project, model, initialText, context);
    myCheckBoxVisible = isCheckboxVisible;
  }

  protected void initUI(ChooseByNamePopupComponent.Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
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
    return myCheckBoxVisible;
  }

  public JPanel getPanel(){
    return myPanel;
  }

}
