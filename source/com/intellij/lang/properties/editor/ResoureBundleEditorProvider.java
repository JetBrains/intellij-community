package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

public class ResoureBundleEditorProvider implements FileEditorProvider, ApplicationComponent {
  public static ResoureBundleEditorProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(ResoureBundleEditorProvider.class);
  }

  public ResoureBundleEditorProvider() {
  }

  public boolean accept(Project project, VirtualFile file){
    return file instanceof ResourceBundleAsVirtualFile;
  }

  public FileEditor createEditor(Project project, final VirtualFile file){
    if (file == null){
      throw new IllegalArgumentException("file cannot be null");
    }
    ResourceBundle resourceBundle = ((ResourceBundleAsVirtualFile)file).getResourceBundle();

    PropertiesFile propertiesFile = resourceBundle.getPropertiesFiles().get(0);
    //todo
    return TextEditorProvider.getInstance().createEditor(project, project.getProjectFile());
  }

  public void disposeEditor(FileEditor editor) {
    TextEditorProvider.getInstance().disposeEditor(editor);
  }

  public FileEditorState readState(Element element, Project project, VirtualFile file){
    return null;
  }

  public void writeState(FileEditorState state, Project project, Element element){
  }

  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.NONE;
  }

  public String getEditorTypeId(){
    return "ResourceBundle";
  }

  public String getComponentName(){
    return "ResourceBundle" + "Provider";
  }

  public void initComponent() { }

  public void disposeComponent(){
  }
}
