package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PathRef extends Tag{

  public PathRef(final String refid) {
    super("path", new Pair[] {pair("refid", refid)});
  }

}
