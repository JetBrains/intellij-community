package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Jar extends Tag {
  public Jar(final String destFile, String duplicate) {
    super("jar", new Pair[] {Pair.create("destfile", destFile), Pair.create("duplicate", duplicate)});
  }
}
