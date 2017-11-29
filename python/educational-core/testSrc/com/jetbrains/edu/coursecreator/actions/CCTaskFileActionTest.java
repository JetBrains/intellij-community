package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.TestActionEvent;
import com.jetbrains.edu.coursecreator.CCTestCase;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

public class CCTaskFileActionTest extends CCTestCase {
  public void testHideTaskFile() {
    VirtualFile virtualFile = configureByTaskFile("taskFile.txt");
    launchAction(virtualFile, new CCHideFromStudent());
    assertNull(StudyUtils.getTaskFile(getProject(), virtualFile));
    UndoManager.getInstance(getProject()).undo(FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile));
    TaskFile taskFile = StudyUtils.getTaskFile(getProject(), virtualFile);
    assertNotNull(taskFile);
    checkHighlighters(taskFile, myFixture.getEditor().getMarkupModel());
  }

  public void testAddTaskFile() {
    VirtualFile virtualFile = copyFileToTask("nonTaskFile.txt");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    launchAction(virtualFile, new CCAddAsTaskFile());
    TaskFile taskFile = StudyUtils.getTaskFile(getProject(), virtualFile);
    assertNotNull(taskFile);
    FileEditor fileEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
    UndoManager.getInstance(getProject()).undo(fileEditor);
    assertNull(StudyUtils.getTaskFile(getProject(), virtualFile));
  }

  private void launchAction(VirtualFile virtualFile, AnAction action) {
    TestActionEvent e = getActionEvent(virtualFile, action);
    action.beforeActionPerformedUpdate(e);
    assertTrue(e.getPresentation().isEnabled() && e.getPresentation().isVisible());
    action.actionPerformed(e);
  }

  @NotNull
  private TestActionEvent getActionEvent(VirtualFile virtualFile, AnAction action) {
    MapDataContext context = new MapDataContext();
    context.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{virtualFile});
    context.put(CommonDataKeys.PROJECT, getProject());
    return new TestActionEvent(context, action);
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/actions/taskFileActions";
  }
}
