package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Property extends Tag {

  public Property(final String name, final String value) {
    super("property", new Pair[] {
      new Pair<String, String>("name", name),
      new Pair<String, String>("value", value)
    });
  }

  public Property(final String filePath) {
    super("property", new Pair[] {
      new Pair<String, String>("file", filePath),
    });
  }

}
