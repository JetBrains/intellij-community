package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 25, 2004
 */
public class AntProject extends Tag {
  public AntProject(String name, String defaultTarget) {
    super("project", new Pair[]{new Pair<String, String>("name", name), new Pair<String, String>("default", defaultTarget)});
  }
}
