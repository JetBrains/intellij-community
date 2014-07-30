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

import com.intellij.openapi.util.text.StringUtil;

public class PyHierarchyCallDataConverter {
  public static PyHierarchyCallerData stringToHierarchyCallerData(String calleePath, String callerData) {
    String[] parts = callerData.split("\t");
    if (parts.length > 2) {
      String calleeName = parts[0];
      String callerPath = parts[1];
      String callerName = parts[2];
      PyHierarchyCallerData data = new PyHierarchyCallerData(callerPath, calleePath, callerName, calleeName);
      for (int i = 3; i < parts.length; i++) {
        data.addCallerLine(Integer.parseInt(parts[i]));
      }

      return data;
    }

    return null;
  }

  public static PyHierarchyCalleeData stringToHierarchyCalleeData(String callerFile, String calleeData) {
    String[] parts = calleeData.split("\t");
    if (parts.length > 2) {
      String callerName = parts[0];
      String calleeFile = parts[1];
      String calleeName = parts[2];
      PyHierarchyCalleeData data = new PyHierarchyCalleeData(callerFile, calleeFile, callerName, calleeName);
      for (int i = 3; i < parts.length; i++) {
        data.addCalleeLine(Integer.parseInt(parts[i]));
      }

      return data;
    }

    return null;
  }

  public static String hierarchyCallerDataToString(PyHierarchyCallerData data) {
    return data.getCalleeName()
           + "\t" + data.getCallerFile()
           + "\t" + data.getCallerName()
           + "\t" + StringUtil.join(data.getCallerLines(), "\t");
  }

  public static String hierarchyCalleeDataToString(PyHierarchyCalleeData data) {
    return data.getCallerName()
           + "\t" + data.getCalleeFile()
           + "\t" + data.getCalleeName()
           + "\t" + StringUtil.join(data.getCalleeLines(), "\t");
  }
}
