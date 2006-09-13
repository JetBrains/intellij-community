package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

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
    SimpleDiffRequest diffData = new SimpleDiffRequest(project, composeName());
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
    presentation.setText(composeName());
    presentation.setEnabled(true);
  }

  private String composeName() {
    if (myLeftIndex == 1 && myRightIndex == 0) {
      return DiffBundle.message("merge.partial.diff.action.name.0.1");
    }
    if (myLeftIndex == 1 && myRightIndex == 2) {
      return DiffBundle.message("merge.partial.diff.action.name.1.2");
    }
      
    return DiffBundle.message("merge.partial.diff.action.name");
  }

  @Nullable
  private static Project projectFromDataContext(DataContext dataContext) {
    return (Project)dataContext.getData(DataConstants.PROJECT);
  }
}
