package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;

/**
 * @author max
 */
public abstract class FileTypeManagerEx extends FileTypeManager{
  public static FileTypeManagerEx getInstanceEx(){
    return (FileTypeManagerEx) ApplicationManager.getApplication().getComponent(FileTypeManager.class);
  }

  public abstract void registerFileType(FileType fileType);
  public abstract void unregisterFileType(FileType fileType);

  public abstract String getIgnoredFilesList();
  public abstract void setIgnoredFilesList(String list);
  public abstract boolean isIgnoredFilesListEqualToCurrent(String list);

  public abstract String getExtension(String fileName);

  public abstract void associateExtension(FileType type, String extension);

  public abstract void fireFileTypesChanged();

  public abstract void fireBeforeFileTypesChanged();
}
