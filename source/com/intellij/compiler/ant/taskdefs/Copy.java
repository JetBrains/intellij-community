package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 23, 2004
 */
public class Copy extends Tag {
  public Copy(String toDir) {
    super("copy", new Pair[] {new Pair<String, String>("todir", toDir)});
  }
  public Copy(String file, String toFile) {
    super("copy", new Pair[] {new Pair<String, String>("file", file), new Pair<String, String>("tofile", toFile)});
  }
}
