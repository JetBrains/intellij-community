package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ResoureBundleEditorProvider implements FileEditorProvider, ApplicationComponent {
  private static final ResourceBundleFileType RESOURCE_BUNDLE_FILE_TYPE = new ResourceBundleFileType();
  public static ResoureBundleEditorProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(ResoureBundleEditorProvider.class);
  }

  public ResoureBundleEditorProvider(Application application, final FileTypeManagerEx fileTypeManagerEx) {
    application.runWriteAction(new Runnable() {
      public void run() {
        fileTypeManagerEx.registerFileType(RESOURCE_BUNDLE_FILE_TYPE);
      }
    });
  }

  public boolean accept(Project project, VirtualFile file){
    return file instanceof ResourceBundleAsVirtualFile;
  }

  @NotNull
  public FileEditor createEditor(Project project, final VirtualFile file){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    ResourceBundle resourceBundle = ((ResourceBundleAsVirtualFile)file).getResourceBundle();
    return new ResourceBundleEditor(project, resourceBundle);
  }

  public void disposeEditor(FileEditor editor) {
    ((ResourceBundleEditor)editor).dispose();
  }

  @NotNull
  public FileEditorState readState(Element element, Project project, VirtualFile file){
    return null;
  }

  public void writeState(FileEditorState state, Project project, Element element){
  }

  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.NONE;
  }

  @NotNull
  public String getEditorTypeId(){
    return "ResourceBundle";
  }

  public String getComponentName(){
    return "ResourceBundle" + "Provider";
  }

  public void initComponent() { }

  public void disposeComponent(){
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileTypeManagerEx.getInstanceEx().unregisterFileType(RESOURCE_BUNDLE_FILE_TYPE);
      }
    });
  }
}
