package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 17, 2004
 */
public class Mkdir extends Tag {
  public Mkdir(String directory) {
    super("mkdir", new Pair[] {new Pair<String, String>("dir", directory)});
  }
}
