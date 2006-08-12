package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RootFileElement extends FileElement {
  private final VirtualFile[] myFiles;
  private final boolean myShowFileSystemRoots;
  private Object[] myChildren;
  @NonNls public static final String A_PREFIX = "a:";

  public RootFileElement(VirtualFile[] files, String name, boolean showFileSystemRoots) {
    super(files.length == 1 ? files[0] : null, name);
    myFiles = files;
    myShowFileSystemRoots = showFileSystemRoots;
  }

  public Object[] getChildren() {
    if (myFiles.length <= 1) {
      return myShowFileSystemRoots ? getFileSystemRoots() : ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    if (myChildren == null) {
      myChildren = createFileElementArray();
    }
    return myChildren;
  }

  private Object[] createFileElementArray() {
    final List<FileElement> roots = new ArrayList<FileElement>();
    for (int i = 0; i < myFiles.length; i++) {
      final VirtualFile file = myFiles[i];
      roots.add(new FileElement(file, file.getPresentableUrl()));
    }
    return roots.toArray(new Object[roots.size()]);
  }

  private static Object[] getFileSystemRoots() {
    File[] roots = File.listRoots();
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    HashSet rootChildren = new HashSet();
    for (int i = 0; i < roots.length; i++) {
      if (StringUtil.startsWithIgnoreCase(roots[i].getPath(), A_PREFIX)) continue;
      String path = roots[i].getAbsolutePath();
      path = path.replace(File.separatorChar, '/');
      VirtualFile file = localFileSystem.findFileByPath(path);
      if (file == null) continue;
      rootChildren.add(new FileElement(file, file.getPresentableUrl()));
    }
    return rootChildren.toArray(new Object[rootChildren.size()]);
  }
}
