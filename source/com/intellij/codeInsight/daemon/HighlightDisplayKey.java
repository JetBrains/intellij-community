package com.intellij.codeInsight.daemon;

import com.intellij.util.containers.HashMap;

public class HighlightDisplayKey {
  private static final HashMap<String,HighlightDisplayKey> ourMap = new HashMap<String, HighlightDisplayKey>();

  public static final HighlightDisplayKey DEPRECATED_SYMBOL = register("DEPRECATED_SYMBOL");
  public static final HighlightDisplayKey UNUSED_IMPORT = register("UNUSED_IMPORT");
  public static final HighlightDisplayKey UNUSED_SYMBOL = register("UNUSED_SYMBOL");
  public static final HighlightDisplayKey UNUSED_THROWS_DECL = register("UNUSED_THROWS");
  public static final HighlightDisplayKey SILLY_ASSIGNMENT = register("SILLY_ASSIGNMENT");
  public static final HighlightDisplayKey ACCESS_STATIC_VIA_INSTANCE = register("ACCESS_STATIC_VIA_INSTANCE");
  public static final HighlightDisplayKey WRONG_PACKAGE_STATEMENT = register("WRONG_PACKAGE_STATEMENT");
  public static final HighlightDisplayKey JAVADOC_ERROR = register("JAVADOC_ERROR");
  public static final HighlightDisplayKey UNKNOWN_JAVADOC_TAG = register("UNKNOWN_JAVADOC_TAG");
  public static final HighlightDisplayKey EJB_ERROR = register("EJB_ERROR");
  public static final HighlightDisplayKey EJB_WARNING = register("EJB_WARNING");
  public static final HighlightDisplayKey ILLEGAL_DEPENDENCY = register("ILLEGAL_DEPENDENCY");

  private final String myName;

  public static HighlightDisplayKey find(String name){
    return ourMap.get(name);
  }

  public static HighlightDisplayKey register(String name) {
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    return new HighlightDisplayKey(name);
  }

  private HighlightDisplayKey(String name) {
    myName = name;
    ourMap.put(myName, this);
  }

  public String toString() {
    return myName;
  }
}
