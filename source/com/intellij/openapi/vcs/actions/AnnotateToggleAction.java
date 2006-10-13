package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;

import java.util.Collection;

/**
 * author: lesya
 */
public class AnnotateToggleAction extends ToggleAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.actions.AnnotateToggleAction");

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(PeerFactory.getInstance().getVcsContextFactory().createContextOn(e)));
  }

  private static boolean isEnabled(final VcsContext context) {
    VirtualFile[] selectedFiles = context.getSelectedFiles();
    if (selectedFiles == null) return false;
    if (selectedFiles.length == 0) return false;
    VirtualFile file = selectedFiles[0];
    if (file.isDirectory()) return false;
    Project project = context.getProject();
    if (project == null) return false;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return false;
    final AnnotationProvider annotationProvider = vcs.getAnnotationProvider();
    if (annotationProvider == null) return false;
    return hasTextEditor(file);

  }

  private static boolean hasTextEditor(VirtualFile selectedFile) {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType fileType = fileTypeManager.getFileTypeByFile(selectedFile);
    return !fileType.isBinary() && fileType != StdFileTypes.GUI_DESIGNER_FORM;
  }

  public boolean isSelected(AnActionEvent e) {
    VcsContext context = PeerFactory.getInstance().getVcsContextFactory().createContextOn(e);
    Editor editor = context.getEditor();
    if (editor == null) return false;
    Collection annotations = editor.getUserData(AnnotateAction.KEY_IN_EDITOR);
    if (annotations == null) return false;
    return !annotations.isEmpty();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    VcsContext context = PeerFactory.getInstance().getVcsContextFactory().createContextOn(e);
    Editor editor = context.getEditor();
    if (!state) {
      if (editor != null) {
        editor.getGutter().closeAllAnnotations();
      }
    }
    else {
      if (editor == null) {
        VirtualFile selectedFile = context.getSelectedFile();
        FileEditor[] fileEditors = FileEditorManager.getInstance(context.getProject()).openFile(selectedFile, false);
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor) {
            editor = ((TextEditor)fileEditor).getEditor();
          }
        }
      }

      LOG.assertTrue(editor != null);

      new AnnotateAction(editor).actionPerformed(e);

    }
  }
}
