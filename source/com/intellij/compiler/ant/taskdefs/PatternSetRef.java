package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PatternSetRef extends Tag{
  public PatternSetRef(final String refid) {
    super("patternset", new Pair[] {new Pair<String, String>("refid", refid)});
  }
}
