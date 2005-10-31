/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface Converter<T> {
  T fromString(String s);
  String toString(T t);

  Converter<Integer> INTEGER_CONVERTER = new Converter<Integer>() {
    public Integer fromString(final String s) {
      return Integer.parseInt(s);
    }

    public String toString(final Integer t) {
      return t.toString();
    }
  };

  Converter<Boolean> BOOLEAN_CONVERTER = new Converter<Boolean>() {
    public Boolean fromString(final String s) {
      return Boolean.parseBoolean(s);
    }

    public String toString(final Boolean t) {
      return t.toString();
    }
  };

  Converter<String> EMPTY_CONVERTER = new Converter<String>() {
    public String fromString(final String s) {
      return s;
    }

    public String toString(final String t) {
      return t;
    }
  };
}
