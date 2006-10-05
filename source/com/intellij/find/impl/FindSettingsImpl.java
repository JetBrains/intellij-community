package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.FindBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.*;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.highlighter.NewJspFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.properties.PropertiesFileType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FindSettingsImpl extends FindSettings implements ApplicationComponent, JDOMExternalizable{
  @NonNls public static final String FIND_DIRECTION_FORWARD = "forward";
  @NonNls public static final String FIND_DIRECTION_BACKWARD = "backward";
  @NonNls public static final String FIND_ORIGIN_FROM_CURSOR = "from_cursor";
  @NonNls public static final String FIND_ORIGIN_ENTIRE_SCOPE = "entire_scope";
  @NonNls public static final String FIND_SCOPE_GLOBAL = "global";
  @NonNls public static final String FIND_SCOPE_SELECTED = "selected";
  public static final String DEFAULT_SEARCH_SCOPE = FindBundle.message("find.scope.all.project.classes");
  
  private static final int MAX_RECENT_SIZE = 30;


  public boolean isSearchOverloadedMethods() {
    return SEARCH_OVERLOADED_METHODS;
  }

  public void setSearchOverloadedMethods(boolean search) {
    SEARCH_OVERLOADED_METHODS = search;
  }

  public boolean SEARCH_OVERLOADED_METHODS = false;
  public boolean SEARCH_IN_LIBRARIES = false;
  public boolean SEARCH_FOR_TEXT_OCCURENCES = true;
  public boolean SKIP_RESULTS_WHEN_ONE_USAGE = false;

  public String FIND_DIRECTION = FIND_DIRECTION_FORWARD;
  public String FIND_ORIGIN = FIND_ORIGIN_FROM_CURSOR;
  public String FIND_SCOPE = FIND_SCOPE_GLOBAL;

  public boolean CASE_SENSITIVE_SEARCH = false;
  public boolean PRESERVE_CASE_REPLACE = false;
  public boolean WHOLE_WORDS_ONLY = false;
  public boolean REGULAR_EXPRESSIONS = false;
  public boolean WITH_SUBDIRECTORIES = true;

  public String SEARCH_SCOPE = DEFAULT_SEARCH_SCOPE;
  public String FILE_MASK;

  public JDOMExternalizableStringList RECENT_FIND_STRINGS = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList RECENT_REPLACE_STRINGS = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList RECENT_DIR_STRINGS = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList RECENT_FILE_MASKS = new JDOMExternalizableStringList();

  @NotNull
  public String getComponentName(){
    return "FindSettings";
  }

  public void initComponent() { }

  public void disposeComponent(){
  }

  public void readExternal(Element element) throws InvalidDataException{
    DefaultJDOMExternalizer.readExternal(this, element);
    if (RECENT_FILE_MASKS.isEmpty()) {
      RECENT_FILE_MASKS.add("*" + PropertiesFileType.DOT_DEFAULT_EXTENSION);
      RECENT_FILE_MASKS.add("*" + HtmlFileType.DOT_DEFAULT_EXTENSION);
      RECENT_FILE_MASKS.add("*" + NewJspFileType.DOT_DEFAULT_EXTENSION);
      RECENT_FILE_MASKS.add("*" + XmlFileType.DOT_DEFAULT_EXTENSION);
      RECENT_FILE_MASKS.add("*" + JavaFileType.DOT_DEFAULT_EXTENSION);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException{
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean isSkipResultsWithOneUsage(){
    return SKIP_RESULTS_WHEN_ONE_USAGE;
  }

  public void setSkipResultsWithOneUsage(boolean skip){
    SKIP_RESULTS_WHEN_ONE_USAGE = skip;
  }

  public boolean isSearchForTextOccurences(){
    return SEARCH_FOR_TEXT_OCCURENCES;
  }

  public void setSearchForTextOccurences(boolean search){
    SEARCH_FOR_TEXT_OCCURENCES = search;
  }

  public String getDefaultScopeName() {
    return SEARCH_SCOPE;
  }

  public void setDefaultScopeName(String scope) {
    SEARCH_SCOPE = scope;
  }

  public boolean isForward(){
    return FIND_DIRECTION_FORWARD.equals(FIND_DIRECTION);
  }

  public void setForward(boolean findDirectionForward){
    FIND_DIRECTION = findDirectionForward ? FIND_DIRECTION_FORWARD : FIND_DIRECTION_BACKWARD;
  }

  public boolean isFromCursor(){
    return FIND_ORIGIN_FROM_CURSOR.equals(FIND_ORIGIN);
  }

  public void setFromCursor(boolean findFromCursor){
    FIND_ORIGIN = findFromCursor ? FIND_ORIGIN_FROM_CURSOR : FIND_ORIGIN_ENTIRE_SCOPE;
  }

  public boolean isGlobal(){
    return FIND_SCOPE_GLOBAL.equals(FIND_SCOPE);
  }

  public void setGlobal(boolean findGlobalScope){
    FIND_SCOPE = findGlobalScope ? FIND_SCOPE_GLOBAL : FIND_SCOPE_SELECTED;
  }

  public boolean isCaseSensitive(){
    return CASE_SENSITIVE_SEARCH;
  }

  public void setCaseSensitive(boolean caseSensitiveSearch){
    CASE_SENSITIVE_SEARCH = caseSensitiveSearch;
  }

  public boolean isPreserveCase() {
    return PRESERVE_CASE_REPLACE;
  }

  public void setPreserveCase(boolean preserveCase) {
    PRESERVE_CASE_REPLACE = preserveCase;
  }

  public boolean isWholeWordsOnly(){
    return WHOLE_WORDS_ONLY;
  }

  public void setWholeWordsOnly(boolean wholeWordsOnly){
    WHOLE_WORDS_ONLY = wholeWordsOnly;
  }

  public boolean isRegularExpressions(){
    return REGULAR_EXPRESSIONS;
  }

  public void setRegularExpressions(boolean regularExpressions){
    REGULAR_EXPRESSIONS = regularExpressions;
  }

  public void setWithSubdirectories(boolean b){
    WITH_SUBDIRECTORIES = b;
  }

  public boolean isWithSubdirectories(){
    return WITH_SUBDIRECTORIES;
  }

  public void initModelBySetings(FindModel model){
    model.setCaseSensitive(isCaseSensitive());
    model.setForward(isForward());
    model.setFromCursor(isFromCursor());
    model.setGlobal(isGlobal());
    model.setRegularExpressions(isRegularExpressions());
    model.setWholeWordsOnly(isWholeWordsOnly());
    model.setWithSubdirectories(isWithSubdirectories());
    model.setFileFilter(FILE_MASK);
  }

  private static void addStringToList(String str, List<String> list, int maxSize){
    if(list.contains(str)){
      list.remove(str);
    }
    list.add(str);
    while(list.size() > maxSize){
      list.remove(0);
    }
  }

  public void addStringToFind(String s){
    if (s == null || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0){
      return;
    }
    addStringToList(s, RECENT_FIND_STRINGS, MAX_RECENT_SIZE);
  }

  public void addStringToReplace(String s) {
    if (s == null || s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0){
      return;
    }
    addStringToList(s, RECENT_REPLACE_STRINGS, MAX_RECENT_SIZE);
  }

  public void addDirectory(String s) {
    if (s == null || s.length() == 0){
      return;
    }
    addStringToList(s, RECENT_DIR_STRINGS, MAX_RECENT_SIZE);
  }

  public String[] getRecentFindStrings(){
    return RECENT_FIND_STRINGS.toArray(new String[RECENT_FIND_STRINGS.size()]);
  }

  public String[] getRecentReplaceStrings(){
    return RECENT_REPLACE_STRINGS.toArray(new String[RECENT_REPLACE_STRINGS.size()]);
  }

  public String[] getRecentFileMasks() {
    return RECENT_FILE_MASKS.toArray(new String[RECENT_FILE_MASKS.size()]);
  }

  public ArrayList<String> getRecentDirectories(){
    return new ArrayList<String>(RECENT_DIR_STRINGS);
  }

  public String getFileMask() {
    return FILE_MASK;
  }

  public void setFileMask(String _fileMask) {
    FILE_MASK = _fileMask;
    if (_fileMask != null && _fileMask.length() > 0) {
      addStringToList(_fileMask, RECENT_FILE_MASKS, MAX_RECENT_SIZE);
    }
  }
}
