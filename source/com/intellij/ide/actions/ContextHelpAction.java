package com.intellij.ide.actions;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class ContextHelpAction extends AnAction {
  private static final Icon myIcon=IconLoader.getIcon("/actions/help.png");
  private final String myHelpID;

  public ContextHelpAction() {
    this(null);
  }

  public ContextHelpAction(String helpID) {
    myHelpID = helpID;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    String helpId = myHelpID == null ? (String)dataContext.getData(DataConstantsEx.HELP_ID) : myHelpID;
    if (helpId != null) {
      HelpManager.getInstance().invokeHelp(helpId);
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    if (ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      DataContext dataContext = event.getDataContext();
      String helpId = myHelpID == null ? (String)dataContext.getData(DataConstantsEx.HELP_ID) : myHelpID;
      presentation.setEnabled(helpId != null);
    }
    else {
      presentation.setIcon(myIcon);
      presentation.setText("Help");
    }
  }
}
