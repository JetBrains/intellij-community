package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Param extends Tag {
  public Param(final String name, final String value) {
    super("param", new Pair[] {
      new Pair<String, String>("name", name),
      new Pair<String, String>("value", value)
    });
  }
  
}
