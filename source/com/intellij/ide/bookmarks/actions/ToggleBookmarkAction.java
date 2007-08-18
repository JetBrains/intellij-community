package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.bookmarks.BookmarkContainer;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.bookmarks.EditorBookmark;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;

public class ToggleBookmarkAction extends AnAction {
  public ToggleBookmarkAction() {
    super(IdeBundle.message("action.toggle.bookmark"));
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    BookmarkManager bookmarkManager = BookmarkManager.getInstance(project);

    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      Editor editor = DataKeys.EDITOR.getData(dataContext);
      // toggle editor bookmark if editor is active
      if (editor != null) {
        EditorBookmark bookmark = bookmarkManager.findEditorBookmark(editor.getDocument(), editor.getCaretModel().getLogicalPosition().line);
        if (bookmark == null) {
          bookmarkManager.addEditorBookmark(editor, editor.getCaretModel().getLogicalPosition().line, EditorBookmark.NOT_NUMBERED);
        }
        else {
          bookmarkManager.removeBookmark(bookmark);
        }
      }
      return;
    }
    String id=ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      PsiElement element = ProjectView.getInstance(project).getParentOfCurrentSelection();
      if (element != null) {
        bookmarkManager.addCommanderBookmark(element);
      }
      return;
    }

    BookmarkContainer container = e.getData(BookmarkContainer.KEY);
    if (container != null) {
      container.toggleBookmark();
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    String s = IdeBundle.message("action.toggle.bookmark");

    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setText(s);
      return;
    }

    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      presentation.setEnabled(dataContext.getData(DataConstants.EDITOR) != null);
      presentation.setText(s);
      return;
    }

    ProjectView projectView = ProjectView.getInstance(project);
    presentation.setText(IdeBundle.message("action.set.bookmark"));
    String id=ToolWindowManager.getInstance(project).getActiveToolWindowId();
    BookmarkContainer container = event.getData(BookmarkContainer.KEY);
    if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      presentation.setEnabled(projectView.getParentOfCurrentSelection() != null);
    }
    else if (container != null) {
      presentation.setEnabled(container.canToggleBookmark());
    }
    else {
      presentation.setEnabled(false);
    }
  }
}