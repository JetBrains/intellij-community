package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 24, 2004
 */
public class Import extends Tag{
  public Import(@NonNls String file, boolean optional) {
    //noinspection HardCodedStringLiteral
    super("import", new Pair[] {new Pair<String, String>("file", file), new Pair<String, String>("optional", optional? "true" : "false")});
  }

  public Import(@NonNls String file) {
    //noinspection HardCodedStringLiteral
    super("import", new Pair[] {new Pair<String, String>("file", file)});
  }
}
