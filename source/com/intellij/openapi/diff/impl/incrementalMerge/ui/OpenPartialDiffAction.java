package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;

import javax.swing.*;

class OpenPartialDiffAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.ui.OpenPartialDiffAction");
  private final int myLeftIndex;
  private final int myRightIndex;

  public OpenPartialDiffAction(int leftIndex, int rightIndex, Icon icon) {
    super("", null, icon);
    myLeftIndex = leftIndex;
    myRightIndex = rightIndex;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    MergePanel2 mergePanel = MergePanel2.fromDataContext(dataContext);
    Project project = projectFromDataContext(dataContext);
    Editor leftEditor = mergePanel.getEditor(myLeftIndex);
    Editor rightEditor = mergePanel.getEditor(myRightIndex);
    FileType type = mergePanel.getContentType();
    SimpleDiffRequest diffData = new SimpleDiffRequest(project, composeName(mergePanel));
    diffData.setContents(new DocumentContent(project, leftEditor.getDocument(), type), new DocumentContent(project, rightEditor.getDocument(), type));
    diffData.setContentTitles(mergePanel.getVersionTitle(myLeftIndex), mergePanel.getVersionTitle(myRightIndex));
    LOG.assertTrue(DiffManagerImpl.INTERNAL_DIFF.canShow(diffData));
    DiffManagerImpl.INTERNAL_DIFF.show(diffData);
  }

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    MergePanel2 mergePanel = MergePanel2.fromDataContext(dataContext);
    Project project = projectFromDataContext(dataContext);
    Presentation presentation = e.getPresentation();
    if (mergePanel == null || project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    presentation.setVisible(true);
    Editor leftEditor = mergePanel.getEditor(myLeftIndex);
    Editor rightEditor = mergePanel.getEditor(myRightIndex);
    if (leftEditor == null || rightEditor == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setText(composeName(mergePanel));
    presentation.setEnabled(true);
  }

  private String composeName(MergePanel2 mergePanel2) {
    return DiffBundle.message("merge.partial.diff.action.name");
  }

  private Project projectFromDataContext(DataContext dataContext) {
    return (Project)dataContext.getData(DataConstants.PROJECT);
  }
}
