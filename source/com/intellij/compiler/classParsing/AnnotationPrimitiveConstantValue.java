package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 2, 2004
 */
public class AnnotationPrimitiveConstantValue extends ConstantValue{
  private final char myValueTag;
  private final ConstantValue myValue;

  public AnnotationPrimitiveConstantValue(char valueTag, ConstantValue value) {
    myValueTag = valueTag;
    myValue = value;
  }

  public AnnotationPrimitiveConstantValue(DataInput in) throws IOException {
    myValueTag = in.readChar();
    myValue = MemberInfoExternalizer.loadConstantValue(in);
  }

  public char getValueTag() {
    return myValueTag;
  }

  public ConstantValue getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    out.writeChar(myValueTag);
    MemberInfoExternalizer.saveConstantValue(out, myValue);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnnotationPrimitiveConstantValue)) return false;

    final AnnotationPrimitiveConstantValue memberValue = (AnnotationPrimitiveConstantValue)o;

    if (myValueTag != memberValue.myValueTag) return false;
    if (!myValue.equals(memberValue.myValue)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (int)myValueTag;
    result = 29 * result + myValue.hashCode();
    return result;
  }

}
