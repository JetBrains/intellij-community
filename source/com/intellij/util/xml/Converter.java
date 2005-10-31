/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface Converter<T> {
  T fromString(String s, final ConvertContext context);
  String toString(T t, final ConvertContext context);

  Converter<Integer> INTEGER_CONVERTER = new Converter<Integer>() {
    public Integer fromString(final String s, final ConvertContext context) {
      return Integer.parseInt(s);
    }

    public String toString(final Integer t, final ConvertContext context) {
      return t.toString();
    }
  };

  Converter<Boolean> BOOLEAN_CONVERTER = new Converter<Boolean>() {
    public Boolean fromString(final String s, final ConvertContext context) {
      return Boolean.parseBoolean(s);
    }

    public String toString(final Boolean t, final ConvertContext context) {
      return t.toString();
    }
  };

  Converter<String> EMPTY_CONVERTER = new Converter<String>() {
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    public String toString(final String t, final ConvertContext context) {
      return t;
    }
  };
}
