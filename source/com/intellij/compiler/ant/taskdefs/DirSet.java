package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class DirSet extends Tag{

  public DirSet(final String dir) {
    super("dirset", new Pair[] {new Pair<String, String>("dir", dir)});
  }
}
