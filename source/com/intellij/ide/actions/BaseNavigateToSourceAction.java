package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;

public abstract class BaseNavigateToSourceAction extends AnAction {
  public static final String SOURCE_NAVIGATOR = "sourceNavigator";
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.BaseNavigateToSourceAction");
  private final boolean myFocusEditor;

  public BaseNavigateToSourceAction(boolean focusEditor) {
    myFocusEditor = focusEditor;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
    navigatable.navigate(myFocusEditor);
  }


  public void update(AnActionEvent event){
    DataContext dataContext = event.getDataContext();
    Navigatable navigatable = (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE);
    event.getPresentation().setEnabled(navigatable != null && navigatable.canNavigate());
  }

  private static void navigate(DataContext dataContext, OpenFileDescriptor descriptor, boolean focusEditor) {
    SourceNavigator.fromContext(dataContext).navigate(descriptor, focusEditor);
  }

  public static abstract class SourceNavigator {
    public abstract void navigate(OpenFileDescriptor descriptor, boolean focusEditor);
    public abstract boolean canNavigateTo(OpenFileDescriptor descriptor);

    public static SourceNavigator fromContext(DataContext dataContext) {
      SourceNavigator sourceNavigator = (SourceNavigator)dataContext.getData(SOURCE_NAVIGATOR);
      if (sourceNavigator != null) return sourceNavigator;
      Boolean isLockedValue = (Boolean)dataContext.getData(DataConstantsEx.SOURCE_NAVIGATION_LOCKED);
      boolean isLocked = isLockedValue != null && isLockedValue.booleanValue();
      if (isLocked) {
        Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
        return editor != null ? new EditorNavigator(editor) : DISABLED_NAVIGATOR;
      }
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      if (project == null) return DISABLED_NAVIGATOR;
      return new DefaultNavigator(project);
    }
  }

  private static final SourceNavigator DISABLED_NAVIGATOR = new SourceNavigator() {
    public void navigate(OpenFileDescriptor descriptor, boolean focusEditor) {
      LOG.error(String.valueOf(descriptor.getFile()));
    }

    public boolean canNavigateTo(OpenFileDescriptor descriptor) {
      return false;
    }
  };

  private static class DefaultNavigator extends SourceNavigator {
    private final Project myProject;

    public DefaultNavigator(Project project) {
      myProject = project;
    }

    public void navigate(OpenFileDescriptor descriptor, boolean focusEditor) {
      FileEditorManager.getInstance(myProject).openTextEditor(descriptor, focusEditor);
    }

    public boolean canNavigateTo(OpenFileDescriptor descriptor) {
      FileEditorProviderManager providerManager = FileEditorProviderManager.getInstance();
      return descriptor != null && providerManager.getProviders(myProject, descriptor.getFile()).length > 0;
    }
  }

  private static class EditorNavigator extends SourceNavigator {
    private final Editor myEditor;

    public EditorNavigator(Editor editor) {
      myEditor = editor;
    }

    public boolean canNavigateTo(OpenFileDescriptor descriptor) {
      if (descriptor == null) return false;
      return descriptor.getFile() == FileDocumentManager.getInstance().getFile(myEditor.getDocument());
    }

    public void navigate(OpenFileDescriptor descriptor, boolean focusEditor) {
      Document document = myEditor.getDocument();
      LogicalPosition position;
      int offset = descriptor.getOffset();
      if (offset < 0)
        position = new LogicalPosition(Math.min(document.getLineCount() - 1, descriptor.getLine()),
                                       descriptor.getColumn());
       else
        position = myEditor.offsetToLogicalPosition(Math.min(document.getTextLength(), offset));
      myEditor.getCaretModel().moveToLogicalPosition(position);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }
}
