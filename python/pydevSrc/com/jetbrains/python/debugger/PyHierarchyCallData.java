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


public class PyHierarchyCallData {
  private final String myCallerFile;
  private final String myCallerName;
  private final int myCallerLine;
  private final String myCalleeFile;
  private final String myCalleeName;
  private final int myCalleeLine;

  public PyHierarchyCallData(String callerFile, String callerName, int callerLine, String calleeFile, String calleeName, int calleeLine) {
    myCallerFile = callerFile;
    myCallerName = callerName;
    myCallerLine = callerLine;
    myCalleeFile = calleeFile;
    myCalleeName = calleeName;
    myCalleeLine = calleeLine;
  }

  public String getCallerFile() {
    return myCallerFile;
  }

  public String getCallerName() {
    return myCallerName;
  }

  public int getCallerLine() {
    return myCallerLine;
  }

  public String getCalleeFile() {
    return myCalleeFile;
  }

  public String getCalleeName() {
    return myCalleeName;
  }

  public int getCalleeLine() {
    return myCalleeLine;
  }

  public PyHierarchyCallerData toPyHierarchyCallerData() {
    PyHierarchyCallerData data = new PyHierarchyCallerData(myCallerFile, myCalleeFile, myCallerName, myCalleeName);
    data.addCallerLine(myCallerLine);

    return data;
  }

  public PyHierarchyCalleeData toPyHierarchyCalleeData() {
    PyHierarchyCalleeData data = new PyHierarchyCalleeData(myCallerFile, myCalleeFile, myCallerName, myCalleeName);
    data.addCalleeLine(myCallerLine);

    return data;
  }

  public String toString() {
    return "Call info: \n"
           + "caller file: " + getCallerFile() + "\n"
           + "callee file: " + getCalleeFile() + "\n"
           + "caller name: " + getCallerName() + "\n"
           + "callee name: " + getCalleeName() + "\n"
           + "caller line: " + getCallerLine();
  }
}
