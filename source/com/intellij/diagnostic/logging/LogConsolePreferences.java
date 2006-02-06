/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.diagnostic.logging;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Pattern;

/**
 * User: anna
 * Date: 06-Feb-2006
 */
public class LogConsolePreferences implements ApplicationComponent, JDOMExternalizable {
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

  public static LogConsolePreferences getInstance(){
    return ApplicationManager.getApplication().getComponent(LogConsolePreferences.class);
  }

  public boolean isFilter(String filter){
    if (filter.compareTo(ERROR) == 0) return FILTER_ERRORS;
    if (filter.compareTo(WARNING) == 0) return FILTER_WARNINGS;
    if (filter.compareTo(INFO) == 0) return FILTER_INFO;
    return true;
  }

  public void setFilter(String filter, boolean state){
    if (filter.compareTo(ERROR) == 0){
      FILTER_ERRORS = state;
    }
    if (filter.compareTo(WARNING) == 0){
      FILTER_WARNINGS = state;
    }
    if (filter.compareTo(INFO) == 0){
      FILTER_INFO = state;
    }
  }

  public void updateCustomFilter(String customFilter) {
    CUSTOM_FILTER = customFilter;
  }

  public boolean isApplicable(String text, String prevType){
    if (CUSTOM_FILTER != null) { 
      if (!Pattern.compile(".*" + CUSTOM_FILTER + ".*").matcher(text).matches()) return false;
    }
    if (ERROR_PATTERN.matcher(text).matches()) return !FILTER_ERRORS;
    if (WARNING_PATTERN.matcher(text).matches()) return !FILTER_WARNINGS;
    if (INFO_PATTERN.matcher(text).matches()) return !FILTER_INFO;
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


  @NonNls
  public String getComponentName() {
    return "LogConsolePreferences";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

}
