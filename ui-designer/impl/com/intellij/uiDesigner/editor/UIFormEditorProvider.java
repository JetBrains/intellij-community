package com.intellij.uiDesigner.editor;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class UIFormEditorProvider implements FileEditorProvider, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.editor.UIFormEditorProvider");

  public boolean accept(final Project project, final VirtualFile file){
    return
      FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.GUI_DESIGNER_FORM &&
      !StdFileTypes.GUI_DESIGNER_FORM.isBinary() &&
      VfsUtil.getModuleForFile(project, file) != null;
  }

  @NotNull public FileEditor createEditor(final Project project, final VirtualFile file){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    LOG.assertTrue(accept(project, file));
    return new UIFormEditor(project, file);
  }

  public void disposeEditor(final FileEditor editor){
    if (editor == null){
      throw new IllegalArgumentException("editor cannot be null");
    }
    ((UIFormEditor)editor).dispose();
  }

  @NotNull
  public FileEditorState readState(final Element element, final Project project, final VirtualFile file){
    //TODO[anton,vova] implement
    return new MyEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void writeState(final FileEditorState state, final Project project, final Element element){
    //TODO[anton,vova] implement
  }

  @NotNull public String getEditorTypeId(){
    return "ui-designer";
  }

  @NotNull public FileEditorPolicy getPolicy() {
    return
      ApplicationManagerEx.getApplicationEx().isInternal() ?
      FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR : FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

  @NotNull
  public String getComponentName(){
    return "uiDesignerEditorProvider";
  }

  public void initComponent() {}

  public void disposeComponent(){}
}
