package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class PatternSet extends Tag{
  public PatternSet(final String id) {
    super("patternset", new Pair[] {new Pair<String, String>("id", id)});
  }
}
