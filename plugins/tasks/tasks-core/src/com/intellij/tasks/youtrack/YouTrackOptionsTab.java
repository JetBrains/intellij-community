package com.intellij.tasks.youtrack;

import com.intellij.openapi.ui.Messages;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class YouTrackOptionsTab {
  public static final String MESSAGE = "<html><em>" +
                                       "Take a note this option should be used only if &quot;Name&quot; property for" +
                                       "&quot;State&quot; field was changed. It's not related to localization settings." +
                                       "</em></html>";


  private JTextField myInProgressState;
  private JTextField myResolvedState;
  private JPanel myRootPanel;
  private JTextPane myNoteText;



  public YouTrackOptionsTab() {
    Messages.installHyperlinkSupport(myNoteText);
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
