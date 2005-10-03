package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public class HighlightDisplayKey {
  private static final HashMap<String,HighlightDisplayKey> ourMap = new HashMap<String, HighlightDisplayKey>();
  private static final Map<HighlightDisplayKey, String>  ourKeyToDisplayNameMap = new HashMap<HighlightDisplayKey, String>();

  public static final HighlightDisplayKey DEPRECATED_SYMBOL = register("DEPRECATED_SYMBOL", InspectionsBundle.message("deprecated.symbol"), "deprecation");
  public static final HighlightDisplayKey UNUSED_IMPORT = register("UNUSED_IMPORT", InspectionsBundle.message("unused.import"));  //no suppress
  public static final HighlightDisplayKey UNUSED_SYMBOL = register("UNUSED_SYMBOL", InspectionsBundle.message("unused.symbol"));
  public static final HighlightDisplayKey UNUSED_THROWS_DECL = register("UNUSED_THROWS", InspectionsBundle.message("unused.throws.declaration"));
  public static final HighlightDisplayKey SILLY_ASSIGNMENT = register("SILLY_ASSIGNMENT", InspectionsBundle.message("assignment.to.self"));
  public static final HighlightDisplayKey ACCESS_STATIC_VIA_INSTANCE = register("ACCESS_STATIC_VIA_INSTANCE", InspectionsBundle.message("access.static.via.instance"));
  public static final HighlightDisplayKey WRONG_PACKAGE_STATEMENT = register("WRONG_PACKAGE_STATEMENT", InspectionsBundle.message("wrong.package.statement")); //no suppress
  public static final HighlightDisplayKey JAVADOC_ERROR = register("JAVADOC_ERROR", InspectionsBundle.message("javadoc.errors"));   //no suppress
  public static final HighlightDisplayKey UNKNOWN_JAVADOC_TAG = register("UNKNOWN_JAVADOC_TAG", InspectionsBundle.message("unknown.javadoc.tags"));  //no suppress

  public static final HighlightDisplayKey CUSTOM_HTML_TAG = register("CUSTOM_HTML_TAG", InspectionsBundle.message("custom.html.tags"));  //no suppress
  public static final HighlightDisplayKey CUSTOM_HTML_ATTRIBUTE = register("CUSTOM_HTML_ATTRIBUTE", InspectionsBundle.message("custom.html.attributes"));  //no suppress
  public static final HighlightDisplayKey REQUIRED_HTML_ATTRIBUTE = register("REQUIRED_HTML_ATTRIBUTE", InspectionsBundle.message("required.html.attributes"));  //no suppress

  public static final HighlightDisplayKey EJB_ERROR = register("EJB_ERROR", InspectionsBundle.message("ejb.errors"));
  public static final HighlightDisplayKey EJB_WARNING = register("EJB_WARNING", InspectionsBundle.message("ejb.warnings"));
  public static final HighlightDisplayKey ILLEGAL_DEPENDENCY = register("ILLEGAL_DEPENDENCY", InspectionsBundle.message("illegal.package.dependencies"));
  public static final HighlightDisplayKey UNCHECKED_WARNING = register("UNCHECKED_WARNING", InspectionsBundle.message("unchecked.warning"), "unchecked");


  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(@NonNls String name){
    return ourMap.get(name);
  }

  public static HighlightDisplayKey register(@NonNls String name) {
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    return new HighlightDisplayKey(name);
  }

  public static HighlightDisplayKey register(@NonNls String name, String displayName, @NonNls String id){
    if (find(name) != null) throw new IllegalArgumentException("Key already registered");
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  public static HighlightDisplayKey register(@NonNls String name, String displayName){
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

  public HighlightDisplayKey(@NonNls final String name, @NonNls final String ID) {
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
