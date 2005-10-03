package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 25, 2004
 */
public class Dirname extends Tag{
  public Dirname(@NonNls String property, @NonNls String file) {
    //noinspection HardCodedStringLiteral
    super("dirname", new Pair[] {new Pair<String, String>("property", property), new Pair<String, String>("file", file)});
  }
}
