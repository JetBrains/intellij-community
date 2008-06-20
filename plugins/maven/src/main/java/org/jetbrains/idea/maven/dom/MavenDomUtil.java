package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class MavenDomUtil {
  public static String calcRelativePath(VirtualFile parent, VirtualFile child) {
    String result = FileUtil.getRelativePath(new File(parent.getPath()),
                                             new File(child.getPath()));
    return FileUtil.toSystemIndependentName(result);
  }
}
