package com.intellij.lang.properties.editor;

import com.intellij.ide.DataManager;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class PropertiesCopyHandlerEx extends PropertiesCopyHandler {
  @Override
  protected void updateBundleEditors(@NotNull String newName,
                                     @NotNull ResourceBundle targetResourceBundle,
                                     @NotNull ResourceBundle sourceResourceBundle,
                                     @NotNull Project project) {
    if (sourceResourceBundle.equals(targetResourceBundle)) {
      DataManager.getInstance()
                 .getDataContextFromFocusAsync()
                 .onSuccess(context -> {
                   final FileEditor fileEditor = PlatformCoreDataKeys.FILE_EDITOR.getData(context);
                   if (fileEditor instanceof ResourceBundleEditor) {
                     final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)fileEditor;
                     resourceBundleEditor.updateTreeRoot();
                     resourceBundleEditor.selectProperty(newName);
                   }
                 });
    } else {
      for (FileEditor editor : FileEditorManager.getInstance(project).openFile(new ResourceBundleAsVirtualFile(targetResourceBundle), true)) {
        ((ResourceBundleEditor) editor).updateTreeRoot();
        ((ResourceBundleEditor) editor).selectProperty(newName);
      }
    }
  }
}