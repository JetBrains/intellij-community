package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class FilePathImpl implements FilePath {
  private VirtualFile myVirtualFile;
  private VirtualFile myVirtualParent;
  private final String myName;
  @NotNull private final File myFile;

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


  public FilePathImpl(final File file) {
    myFile = file;
    myName = file.getName();
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
    if (myVirtualFile != null && myVirtualFile.isValid()) {
      return myVirtualFile.getPath();
    }
    else {
      return myFile.getPath();
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

  public boolean isUnder(FilePath parent, boolean strict) {
    if (myVirtualFile != null && parent.getVirtualFile() != null) {
      return VfsUtil.isAncestor(parent.getVirtualFile(), myVirtualFile, strict);
    }

    try {
      return FileUtil.isAncestor(parent.getIOFile(), getIOFile(), strict);
    }
    catch (IOException e) {
      return false;
    }
  }

  public VirtualFile getVirtualFile() {
    if (myVirtualFile != null && !myVirtualFile.isValid()) {
      myVirtualFile = null;
    }
    return myVirtualFile;
  }

  public VirtualFile getVirtualFileParent() {
    if (myVirtualParent != null && !myVirtualParent.isValid()) {
      myVirtualParent = null;
    }
    return myVirtualParent;
  }

  @NotNull public File getIOFile() {
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
      return new FilePathImpl(selectedFile);
    }

    VirtualFile virtualFileParent = lfs.findFileByIoFile(parentFile);
    if (virtualFileParent != null) {
      return new FilePathImpl(virtualFileParent, selectedFile.getName());
    }
    else {
      return new FilePathImpl(selectedFile);
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
