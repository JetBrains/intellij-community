/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.regex.Pattern;

public class TestClassesFilter {

  private final Map<String, List<Pattern>> myPatterns = new HashMap<String, List<Pattern>>();
  public static final TestClassesFilter EMPTY_CLASSES_FILTER = new TestClassesFilter(new HashMap<String, List<String>>());
  public static final ArrayList<Pattern> EMPTY_LIST = new ArrayList<Pattern>();
  private final List<Pattern> myAllPatterns = new ArrayList<Pattern>();
  public static final String ALL_EXCLUDE_DEFINED = "ALL_EXCLUDE_DEFINED";

  private TestClassesFilter(Map<String, List<String>> filters) {

    for (Iterator<String> eachGroupName = filters.keySet().iterator(); eachGroupName.hasNext();) {
      String groupName = eachGroupName.next();
      List<String> filterList = filters.get(groupName);
      ArrayList<Pattern> patterns = new ArrayList<Pattern>();
      myPatterns.put(groupName, patterns);
      for (Iterator<String> iterator = filterList.iterator(); iterator.hasNext();) {
        String filter = iterator.next().trim();
        if (filter.length() == 0) continue;
        filter = filter.replaceAll("\\*", ".\\*");
        Pattern pattern = Pattern.compile(filter);
        myAllPatterns.add(pattern);
        patterns.add(pattern);
      }
    }
  }

  public static TestClassesFilter createOn(InputStreamReader inputStreamReader) {
    try {
      Map<String, List<String>> groupNameToPatternsMap = new HashMap<String, List<String>>();
      String currentGroupName = "";
      LineNumberReader lineNumberReader = new LineNumberReader(inputStreamReader);
      String line;
      while ((line = lineNumberReader.readLine()) != null) {
        if (line.startsWith("[") && line.endsWith("]")) {
          currentGroupName = line.substring(1, line.length() - 1);
        }
        else {
          if (!groupNameToPatternsMap.containsKey(currentGroupName)) {
            groupNameToPatternsMap.put(currentGroupName, new ArrayList<String>());
          }
          groupNameToPatternsMap.get(currentGroupName).add(line);
        }
      }

      return new TestClassesFilter(groupNameToPatternsMap);
    }
    catch (IOException e) {
      return EMPTY_CLASSES_FILTER;
    }
  }

  private boolean matches(Collection<Pattern> patterns, String className) {
    for (Iterator<Pattern> iterator = patterns.iterator(); iterator.hasNext();) {
      Pattern pattern = iterator.next();
      if (pattern.matcher(className).matches()) {
        return true;
      }
    }
    return false;

  }


  public boolean matches(String className, String groupName) {
    List<Pattern> patterns = collectPatternsFor(groupName);
    boolean result = matches(patterns, className);
    //null group means all patterns from each defined group should be excluded
    if (isAllExcludeDefinedGroup(groupName)) {
      return !result;
    }
    else {
      return result;
    }
  }

  private boolean isAllExcludeDefinedGroup(String groupName) {
    if (groupName == null){
      return true;
    }

    if (ALL_EXCLUDE_DEFINED.equals(groupName)){
      return true;
    }

    return false;
  }

  private List<Pattern> collectPatternsFor(String groupName) {
    if (isAllExcludeDefinedGroup(groupName)){
      return myAllPatterns;
    } else {
      if (!myPatterns.containsKey(groupName)){
        return EMPTY_LIST;
      } else {
        return myPatterns.get(groupName);
      }
    }
  }
}
