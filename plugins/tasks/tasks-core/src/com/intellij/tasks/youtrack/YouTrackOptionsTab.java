package com.intellij.tasks.youtrack;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class YouTrackOptionsTab {
  public static final String MESSAGE = "<html>" +
                                       "This option should be used only when \"Name\" property for \"State\" field " +
                                       "was changed in server settings. It's not related to localized names of states." +
                                       "</html>";

  private JTextField myInProgressState;
  private JTextField myResolvedState;
  private JPanel myRootPanel;
  private JBLabel myNoteText;

  public YouTrackOptionsTab() {
    myNoteText.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myNoteText.setText(MESSAGE);
  }

  public JTextField getInProgressState() {
    return myInProgressState;
  }

  public JTextField getResolvedState() {
    return myResolvedState;
  }

  public JPanel getRootPanel() {
    return myRootPanel;
  }


}
