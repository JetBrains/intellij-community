package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public class Import extends Tag{
  public Import(String file, boolean optional) {
    super("import", new Pair[] {new Pair<String, String>("file", file), new Pair<String, String>("optional", optional? "true" : "false")});
  }

  public Import(String file) {
    super("import", new Pair[] {new Pair<String, String>("file", file)});
  }
}
