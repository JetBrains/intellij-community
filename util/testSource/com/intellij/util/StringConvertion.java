package com.intellij.util;

public abstract class StringConvertion {
  public abstract String convert(Object obj);

  public static StringConvertion DEFAULT = new StringConvertion() {
    public String convert(Object obj) {
      if (obj == null)
        return "null";
      return obj.toString() + " (" + obj.getClass().getName() + ")";
    }
  };

  public static StringConvertion TO_STRING = new StringConvertion() {
    public String convert(Object obj) {
      return obj.toString();
    }
  };
}