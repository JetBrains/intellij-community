package com.intellij.codeInsight.daemon;

import com.intellij.util.containers.HashMap;

import java.util.Map;

public class HighlightDisplayKey {
  private static final HashMap<String,HighlightDisplayKey> ourMap = new HashMap<String, HighlightDisplayKey>();
  private static final Map<HighlightDisplayKey, String>  ourKeyToDisplayNameMap = new HashMap<HighlightDisplayKey, String>();

  public static final HighlightDisplayKey DEPRECATED_SYMBOL = register("DEPRECATED_SYMBOL", "Deprecated symbol", "deprecation");
  public static final HighlightDisplayKey UNUSED_IMPORT = register("UNUSED_IMPORT", "Unused import");  //no suppress
  public static final HighlightDisplayKey UNUSED_SYMBOL = register("UNUSED_SYMBOL", "Unused symbol");
  public static final HighlightDisplayKey UNUSED_THROWS_DECL = register("UNUSED_THROWS", "Unused throws declaration");
  public static final HighlightDisplayKey SILLY_ASSIGNMENT = register("SILLY_ASSIGNMENT", "Silly assignment");
  public static final HighlightDisplayKey ACCESS_STATIC_VIA_INSTANCE = register("ACCESS_STATIC_VIA_INSTANCE", "Access static member via instance reference");
  public static final HighlightDisplayKey WRONG_PACKAGE_STATEMENT = register("WRONG_PACKAGE_STATEMENT", "Wrong package statement"); //no suppress
  public static final HighlightDisplayKey JAVADOC_ERROR = register("JAVADOC_ERROR", "JavaDoc errors");   //no suppress
  public static final HighlightDisplayKey UNKNOWN_JAVADOC_TAG = register("UNKNOWN_JAVADOC_TAG", "Unknown javadoc tags");  //no suppress
  
  public static final HighlightDisplayKey CUSTOM_HTML_TAG = register("CUSTOM_HTML_TAG", "Custom html tags");  //no suppress
  public static final HighlightDisplayKey CUSTOM_HTML_ATTRIBUTE = register("CUSTOM_HTML_ATTRIBUTE", "Custom html attributes");  //no suppress
  public static final HighlightDisplayKey REQUIRED_HTML_ATTRIBUTE = register("REQUIRED_HTML_ATTRIBUTE", "Required html attributes");  //no suppress
  
  public static final HighlightDisplayKey EJB_ERROR = register("EJB_ERROR", "EJB errors");
  public static final HighlightDisplayKey EJB_WARNING = register("EJB_WARNING", "EJB warnings");
  public static final HighlightDisplayKey ILLEGAL_DEPENDENCY = register("ILLEGAL_DEPENDENCY", "Illegal package dependencies");
  public static final HighlightDisplayKey UNCHECKED_WARNING = register("UNCHECKED_WARNING", "Unchecked warning", "unchecked");


  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(String name){
    return ourMap.get(name);
  }

  public static HighlightDisplayKey register(String name) {
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    return new HighlightDisplayKey(name);
  }

  private static HighlightDisplayKey register(String name, String displayName, String id){
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  public static HighlightDisplayKey register(String name, String displayName){
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  public static String getDisplayNameByKey(HighlightDisplayKey key){
    return ourKeyToDisplayNameMap.get(key);
  }

  private HighlightDisplayKey(String name) {
    myName = name;
    myID = myName;
    ourMap.put(myName, this);
  }

  public HighlightDisplayKey(final String name, final String ID) {
    myName = name;
    myID = ID;
    ourMap.put(myName, this);
  }

  public String toString() {
    return myName;
  }

  public String getID(){
    return myID;
  }
}
