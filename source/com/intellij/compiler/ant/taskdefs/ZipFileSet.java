package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.compiler.ant.Tag;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class ZipFileSet extends Tag{
  public ZipFileSet(String fileOrDir, final String relativePath, boolean isDir) {
    super("zipfileset", new Pair[] {
      pair(isDir ? "dir" : "file", fileOrDir),
      pair("prefix", isDir ? relativePath : makeFilePrefix(relativePath))});
  }

  private static String makeFilePrefix(final String fileName) {
    final String parent = new File(fileName).getParent();
    if (parent == null) return "";
    return FileUtil.toSystemIndependentName(parent);
  }
}
