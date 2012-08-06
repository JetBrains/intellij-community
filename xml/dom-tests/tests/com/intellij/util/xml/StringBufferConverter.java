package com.intellij.util.xml;

/**
 * @author peter
 */
public class StringBufferConverter extends Converter<StringBuffer> {
  @Override
  public StringBuffer fromString(final String s, final ConvertContext context) {
    return s==null?null:new StringBuffer(s);
  }

  @Override
  public String toString(final StringBuffer t, final ConvertContext context) {
    return t==null?null:t.toString();
  }

}
