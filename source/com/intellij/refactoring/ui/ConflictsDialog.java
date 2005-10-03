/**
 * created at Sep 12, 2001
 * @author Jeka
 */
package com.intellij.refactoring.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.*;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NonNls;

public class ConflictsDialog extends DialogWrapper{
  private String[] myConflictDescriptions;

  public ConflictsDialog(String[] conflictDescriptions, Project project) {
    super(project, true);
    myConflictDescriptions = conflictDescriptions;
    setTitle(RefactoringBundle.message("problems.detected.title"));
    setOKButtonText(RefactoringBundle.message("continue.button"));
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),new CancelAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    @NonNls final String contentType = "text/html";
    final JEditorPane messagePane = new JEditorPane(contentType, "");
    messagePane.setEditable(false);
    JScrollPane scrollPane = new JScrollPane(messagePane);
    scrollPane.setPreferredSize(new Dimension(500, 400));
    panel.add(new JLabel(RefactoringBundle.message("the.following.problems.were.found")), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);

    @NonNls StringBuffer buf = new StringBuffer();
    for (String description : myConflictDescriptions) {
      buf.append(description);
      buf.append("<br><br>");
    }
    messagePane.setText(buf.toString());
    return panel;
  }

  protected JComponent createSouthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    panel.add(new JLabel(RefactoringBundle.message("do.you.wish.to.ignore.them.and.continue")), BorderLayout.WEST);
    return panel;
  }

  private class CancelAction extends AbstractAction {
    public CancelAction() {
      super(RefactoringBundle.message("cancel.button"));
      putValue(DialogWrapper.DEFAULT_ACTION,Boolean.TRUE);
    }

    public void actionPerformed(ActionEvent e) {
      doCancelAction();
    }
  }
}
