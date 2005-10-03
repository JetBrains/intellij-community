package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Attribute extends Tag{
  public Attribute(@NonNls String name, String value) {
    //noinspection HardCodedStringLiteral
    super("attribute", new Pair[] {Pair.create("name", name),Pair.create("value", value)});
  }
}
