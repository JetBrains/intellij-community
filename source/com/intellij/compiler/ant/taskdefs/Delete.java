package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Delete extends Tag{
  public Delete(String dir) {
    super("delete", new Pair[] {Pair.create("dir", dir)});
  }
}
