package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PathElement extends Tag{

  public PathElement(String dir) {
    super("pathelement", new Pair[]{new Pair<String, String>("location", dir)});
  }

}

