package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Exclude extends Tag {

  public Exclude(final String name) {
    super("exclude", new Pair[] {new Pair<String, String>("name", name)});
  }

}
