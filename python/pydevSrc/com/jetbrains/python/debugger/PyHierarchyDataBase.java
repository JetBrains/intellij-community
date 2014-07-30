/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import com.google.common.collect.Sets;

import java.util.Set;


public class PyHierarchyDataBase {
  private final String myCallerFile;
  private final String myCalleeFile;
  private final String myCallerName;
  private final String myCalleeName;
  private Set<Integer> myLines = Sets.newHashSet();

  protected PyHierarchyDataBase(String callerFile, String calleeFile, String callerName, String calleeName) {
    myCallerFile = callerFile;
    myCalleeFile = calleeFile;
    myCallerName = callerName;
    myCalleeName = calleeName;
  }

  public String getCallerFile() {
    return myCallerFile;
  }

  public String getCallerName() {
    return myCallerName;
  }

  public String getCalleeFile() {
    return myCalleeFile;
  }

  public String getCalleeName() {
    return myCalleeName;
  }

  protected Set<Integer> getLines() {
    return myLines;
  }

  protected void addLine(int line) {
    myLines.add(line);
  }

  protected void addAllLines(PyHierarchyDataBase hierarchyData) {
    for (int line: hierarchyData.getLines()) {
      addLine(line);
    }
  }
}
