package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class FileEditorProviderManager{
  public static FileEditorProviderManager getInstance(){
    return ApplicationManager.getApplication().getComponent(FileEditorProviderManager.class);
  }

  /**
   * @param file cannot be <code>null</code>
   *
   * @return array of all editors providers that can create editor
   * for the specified <code>file</code>. The method returns
   * an empty array if there are no such providers. Please note that returned array
   * is constructed with respect to editor policies.
   */
  public abstract @NotNull FileEditorProvider[] getProviders(@NotNull Project project, @NotNull VirtualFile file);

  /**
   * @return may be null
   */
  public abstract FileEditorProvider getProvider(@NotNull String editorTypeId);
}
