package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;

import java.io.File;
import java.nio.charset.Charset;

public class FilePathImpl implements FilePath {
  private VirtualFile myVirtualFile;
  private final VirtualFile myVirtualParent;
  private final String myName;
  private final File myFile;

  public FilePathImpl(VirtualFile virtualParent, String name) {
    myVirtualParent = virtualParent;
    myName = name;
    if (myVirtualParent == null) {
      myFile = new File(myName);
    }
    else {
      myFile = new File(new File(myVirtualParent.getPath()), myName);
    }

    refresh();
  }

  public int hashCode() {
    return myFile.hashCode();
  }

  public boolean equals(Object o) {
    if (!(o instanceof FilePath)) {
      return false;
    }
    else {
      return myFile.equals(((FilePath)o).getIOFile());
    }
  }

  public FilePathImpl(VirtualFile virtualFile) {
    this(virtualFile.getParent(), virtualFile.getName());
  }

  public void refresh() {
    if (myVirtualParent == null) {
      myVirtualFile = LocalFileSystem.getInstance().findFileByPath(myName);
    }
    else {
      myVirtualFile = myVirtualParent.findChild(myName);
    }

  }

  public String getPath() {
    if (myVirtualFile != null) {
      return myVirtualFile.getPath();
    }
    else {
      return myVirtualParent.getPath() + "/" + myName;
    }
  }

  public boolean isDirectory() {
    if (myVirtualFile == null) {
      return false;
    }
    else {
      return myVirtualFile.isDirectory();
    }
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public VirtualFile getVirtualFileParent() {
    return myVirtualParent;
  }

  public File getIOFile() {
    return myFile;
  }

  public String getName() {
    return myName;
  }

  public String getPresentableUrl() {
    if (myVirtualFile == null) {
      return myFile.getAbsolutePath();
    }
    else {
      return myVirtualFile.getPresentableUrl();
    }
  }

  public Document getDocument() {
    return myVirtualFile != null ? FileDocumentManager.getInstance().getDocument(myVirtualFile) : null;
  }

  public Charset getCharset() {
    return myVirtualFile != null ?
           myVirtualFile.getCharset()
           : CharsetToolkit.getIDEOptionsCharset();
  }

  public FileType getFileType() {
    return myVirtualFile != null
           ? myVirtualFile.getFileType()
           : FileTypeManager.getInstance().getFileTypeByFileName(myFile.getName());
  }

  public static FilePathImpl create(File selectedFile) {
    if (selectedFile == null) {
      return null;
    }

    LocalFileSystem lfs = LocalFileSystem.getInstance();

    VirtualFile virtualFile = lfs.findFileByIoFile(selectedFile);
    if (virtualFile != null) {
      return new FilePathImpl(virtualFile);
    }

    File parentFile = selectedFile.getParentFile();
    if (parentFile == null) {
      return null;
    }

    VirtualFile virtualFileParent = lfs.findFileByIoFile(parentFile);
    if (virtualFileParent != null) {
      return new FilePathImpl(virtualFileParent, selectedFile.getName());
    }
    else {
      return null;
    }
  }

  public static FilePath createOn(String s) {
    File ioFile = new File(s);
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile virtualFile = localFileSystem.findFileByIoFile(ioFile);
    if (virtualFile != null) {
      return new FilePathImpl(virtualFile);
    }
    else {
      VirtualFile virtualFileParent = localFileSystem.findFileByIoFile(ioFile.getParentFile());
      if (virtualFileParent != null) {
        return new FilePathImpl(virtualFileParent, ioFile.getName());
      }
      else {
        return null;
      }
    }
  }
}
