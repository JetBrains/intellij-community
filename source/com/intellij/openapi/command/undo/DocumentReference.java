package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public abstract class DocumentReference {
  public int hashCode() {
    VirtualFile file = getFile();
    return file != null ? file.hashCode() : getDocument().hashCode();
  }

  public boolean equals(Object object) {
    if (!(object instanceof DocumentReference)) return false;
    VirtualFile file1 = getFile();
    VirtualFile file2 = ((DocumentReference) object).getFile();
    if (file1 != null) return file1.equals(file2);
    if (file2 != null) return file2.equals(file1);

    return getDocument().equals(((DocumentReference) object).getDocument());
  }

  public abstract VirtualFile getFile();
  public abstract Document getDocument();
  public abstract void beforeFileDeletion(VirtualFile file);

  protected abstract String getUrl();

  public boolean equalsByUrl(String url) {
    VirtualFile file = getFile();
    if (file == null) return false;
    if (file.isValid()) return false;
    String url1 = getUrl();
    if ((url1 == null) || (url == null)) return false;
    if (SystemInfo.isFileSystemCaseSensitive)
      return url.equals(url1);
    else
      return url.compareToIgnoreCase(url1) == 0;
  }
}
