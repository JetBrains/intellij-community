package com.intellij.ide.actions;

import com.intellij.ui.content.ContentManagerUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.wm.ToolWindowManager;

public class PinActiveTabAction extends ToggleAction {
  /**
   * @return selected editor or <code>null</code>
   */
  private VirtualFile getFile(final DataContext context){
    Project project = (Project)context.getData(DataConstants.PROJECT);
    if(project == null){
      return null;
    }

    // To provide file from editor manager, editor component should be active
    if(!ToolWindowManager.getInstance(project).isEditorComponentActive()){
      return null;
    }

    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile[] selectedFiles = fileEditorManager.getSelectedFiles();
    if(selectedFiles.length != 0){
      return selectedFiles[0];
    }
    else{
      return null;
    }
  }

  /**
   * @return selected content or <code>null</code>
   */
  private Content getContent(final DataContext context){
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(context, true);
    if (contentManager == null){
      return null;
    }
    return contentManager.getSelectedContent();
  }

  public boolean isSelected(AnActionEvent e) {
    DataContext context = e.getDataContext();
    VirtualFile file = getFile(context);
    if(file != null){
      // 1. Check editor
      final Project project = (Project)context.getData(DataConstants.PROJECT);
      final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      return fileEditorManager.getCurrentWindow().isFilePinned(file);
    }
    else{
      // 2. Check content
      final Content content = getContent(context);
      if(content != null){
        return content.isPinned();
      }
      else{
        return false;
      }
    }
  }

  public void setSelected(AnActionEvent e, boolean state) {
    DataContext context = e.getDataContext();
    VirtualFile file = getFile(context);
    if(file != null){
      // 1. Check editor
      Project project = (Project)context.getData(DataConstants.PROJECT);
      FileEditorManagerEx.getInstanceEx(project).getCurrentWindow ().setFilePinned(file, state);
    }
    else{
      Content content = getContent(context); // at this point content cannot be null
      content.setPinned(state);
    }
  }

  public void update(AnActionEvent e){
    super.update(e);
    Presentation presentation = e.getPresentation();
    DataContext context = e.getDataContext();
    presentation.setEnabled(getFile(context) != null || getContent(context) != null);
  }
}
