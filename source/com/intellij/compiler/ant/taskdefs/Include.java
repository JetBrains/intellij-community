package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Include extends Tag {

  public Include(final String name) {
    super("include", new Pair[] {new Pair<String, String>("name", name)});
  }

}
