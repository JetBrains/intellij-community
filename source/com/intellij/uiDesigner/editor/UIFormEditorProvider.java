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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

public final class UIFormEditorProvider implements FileEditorProvider, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.editor.UIFormEditorProvider");
  
  public boolean accept(final Project project, final VirtualFile file){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    return
      FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.GUI_DESIGNER_FORM &&
      !StdFileTypes.GUI_DESIGNER_FORM.isBinary() &&
      ModuleUtil.getModuleForFile(project, file) != null;
  }
  
  public FileEditor createEditor(final Project project, final VirtualFile file){
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

  public FileEditorState readState(final Element element, final Project project, final VirtualFile file){
    //TODO[anton,vova] implement
    return new MyEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void writeState(final FileEditorState state, final Project project, final Element element){
    //TODO[anton,vova] implement
  }

  public String getEditorTypeId(){
    return "ui-designer";
  }

  public FileEditorPolicy getPolicy() {
    return 
      ApplicationManagerEx.getApplicationEx().isInternal() ? 
      FileEditorPolicy.NONE : FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

  public String getComponentName(){
    return "uiDesignerEditorProvider";
  }

  public void initComponent() {}

  public void disposeComponent(){}
}
