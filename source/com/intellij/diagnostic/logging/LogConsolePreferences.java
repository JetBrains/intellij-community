/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.diagnostic.logging;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * User: anna
 * Date: 06-Feb-2006
 */
public class LogConsolePreferences extends LogFilterRegistrar {
  private SortedMap<LogFilter, Boolean> myRegisteredLogFilters = new TreeMap<LogFilter, Boolean>(new Comparator<LogFilter>() {
    public int compare(final LogFilter o1, final LogFilter o2) {
      return -1;
    }
  });
  @NonNls private static final String FILTER = "filter";
  @NonNls private static final String IS_ACTIVE = "is_active";

  public boolean FILTER_ERRORS = false;
  public boolean FILTER_WARNINGS = false;
  public boolean FILTER_INFO = true;
  public String CUSTOM_FILTER = null;
  @NonNls public static final String ERROR = "ERROR";
  @NonNls public static final String WARNING = "WARNING";
  @NonNls public static final String INFO = "INFO";
  @NonNls public static final String CUSTOM = "CUSTOM";

  public final static Pattern ERROR_PATTERN = Pattern.compile(".*" + ERROR + ".*");
  public final static Pattern WARNING_PATTERN = Pattern.compile(".*" + WARNING + ".*");
  public final static Pattern INFO_PATTERN = Pattern.compile(".*" + INFO + ".*");
  @NonNls public final static Pattern EXCEPTION_PATTERN = Pattern.compile(".*at .*");

  public static LogConsolePreferences getInstanceEx(Project project){
    return project.getComponent(LogConsolePreferences.class);
  }

  public void updateCustomFilter(String customFilter) {
    CUSTOM_FILTER = customFilter;
  }

  public boolean isApplicable(String text, String prevType){
    if (CUSTOM_FILTER != null) {
      if (!Pattern.compile(".*" + CUSTOM_FILTER + ".*").matcher(text).matches()) return false;
    }
    if (ERROR_PATTERN.matcher(text).matches() && FILTER_ERRORS) return false;
    if (WARNING_PATTERN.matcher(text).matches() && FILTER_WARNINGS) return false;
    if (INFO_PATTERN.matcher(text).matches() && FILTER_INFO) return false;
    for (LogFilter filter : myRegisteredLogFilters.keySet()) {
      if (myRegisteredLogFilters.get(filter).booleanValue() && !filter.isAcceptable(text)) return false;
    }
    if (prevType != null) {
      if (prevType.equals(ERROR)){
        return !FILTER_ERRORS;
      }
      if (prevType.equals(WARNING)){
        return !FILTER_WARNINGS;
      }
      if (prevType.equals(INFO)){
        return !FILTER_INFO;
      }
    }
    return true;

  }

  public static ConsoleViewContentType getContentType(String type){
    if (type.equals(ERROR)) return ConsoleViewContentType.ERROR_OUTPUT;
    if (type.equals(WARNING) || type.equals(INFO)) return ConsoleViewContentType.NORMAL_OUTPUT;
    return null;
  }

  public static String getType(String text){
    if (ERROR_PATTERN.matcher(text).matches()) return ERROR;
    if (WARNING_PATTERN.matcher(text).matches()) return WARNING;
    if (INFO_PATTERN.matcher(text).matches()) return INFO;
    return null;
  }

  public static Key getProcessOutputTypes(String type){
    if (type.equals(ERROR)) return ProcessOutputTypes.STDERR;
    if (type.equals(WARNING) || type.equals(INFO)) return ProcessOutputTypes.STDOUT;
    return null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    final List children = element.getChildren(FILTER);
    for (Object child : children) {
      Element filterElement = (Element)child;
      final LogFilter filter = new LogFilter();
      filter.readExternal(filterElement);
      setFilterSelected(filter, Boolean.parseBoolean(filterElement.getAttributeValue(IS_ACTIVE)));
    }
     DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (LogFilter filter : myRegisteredLogFilters.keySet()) {
      Element filterElement = new Element(FILTER);
      filterElement.setAttribute(IS_ACTIVE, myRegisteredLogFilters.get(filter).toString());
      filter.writeExternal(filterElement);
      element.addContent(filterElement);
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void registerFilter(LogFilter filter){
    myRegisteredLogFilters.put(filter, Boolean.FALSE);
  }

  public List<LogFilter> getRegisteredLogFilters() {
    return new ArrayList<LogFilter>(myRegisteredLogFilters.keySet());
  }

  public boolean isFilterSelected(LogFilter filter){
    final Boolean isSelected = myRegisteredLogFilters.get(filter);
    if (isSelected != null) {
      return isSelected.booleanValue();
    }
    if (filter.getName().indexOf(ERROR) != -1) return FILTER_ERRORS;
    if (filter.getName().indexOf(WARNING) != -1) return FILTER_WARNINGS;
    return filter.getName().indexOf(INFO) == -1 || FILTER_INFO;
  }

  public void setFilterSelected(LogFilter filter, boolean state){
    if (myRegisteredLogFilters.containsKey(filter)){
      myRegisteredLogFilters.put(filter, state);
    } else {
      String filterName = filter.getName();
      if (filterName.indexOf(ERROR) != -1){
        FILTER_ERRORS = state;
      }
      if (filterName.indexOf(WARNING) != -1){
        FILTER_WARNINGS = state;
      }
      if (filterName.indexOf(INFO) != -1){
        FILTER_INFO = state;
      }
    }
  }

  public void projectOpened() {}

  public void projectClosed() {}


  @NotNull
  @NonNls
  public String getComponentName() {
    return "LogConsolePreferences";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
