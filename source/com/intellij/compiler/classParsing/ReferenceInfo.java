/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.IOException;

public class ReferenceInfo extends ItemInfo {

  public static final ReferenceInfo[] EMPTY_ARRAY = new ReferenceInfo[0];

  public ReferenceInfo(int declaringClassName) {
    super(declaringClassName);
  }

  public ReferenceInfo(DataInput in) throws IOException {
    super(in);
  }

  public String toString() {
    return "Class reference[class name=" + String.valueOf(getClassName()) + "]";
  }
}
