package com.intellij.compiler.ant.taskdefs;

import com.intellij.openapi.util.Pair;
import com.intellij.compiler.ant.Tag;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2004
 */
public class Attribute extends Tag{
  public Attribute(String name, String value) {
    super("attribute", new Pair[] {Pair.create("name", name),Pair.create("value", value)});
  }
}
