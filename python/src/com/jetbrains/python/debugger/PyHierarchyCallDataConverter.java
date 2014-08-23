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
import org.jetbrains.annotations.Nullable;

public class PyHierarchyCallDataConverter {
  public static PyHierarchyCallData callerDataStringToHierarchyCallData(@Nullable String calleePath, String callerData) {
    String[] parts = callerData.split("\t");
    if (parts.length > 3) {
      String calleeName = parts[0];
      String callerPath = parts[1];
      String callerName = parts[2];
      PyHierarchyCallData callData = new PyHierarchyCallData(callerPath, callerName, -1, calleePath, calleeName, -1);
      for (int i = 3; i < parts.length; i++) {
        callData.addCalleeCallLine(Integer.parseInt(parts[i]));
      }

      return callData;
    }

    return null;
  }

  public static PyHierarchyCallData calleeDataStringToHierarchyCallData(@Nullable String callerPath, String calleeData) {
    String[] parts = calleeData.split("\t");
    if (parts.length > 3) {
      String callerName = parts[0];
      String calleePath = parts[1];
      String calleeName = parts[2];
      PyHierarchyCallData callData = new PyHierarchyCallData(callerPath, callerName, -1, calleePath, calleeName, -1);
      for (int i = 3; i < parts.length; i++) {
        callData.addCalleeCallLine(Integer.parseInt(parts[i]));
      }

      return callData;
    }

    return null;
  }

  public static String hierarchyCallDataToCallerDataString(PyHierarchyCallData callData) {
    return callData.getCalleeName()
           + "\t" + callData.getCallerFile()
           + "\t" + callData.getCallerName()
           + "\t" + StringUtil.join(callData.getCalleeCallLines(), "\t");
  }

  public static String hierarchyCallDataToCalleeDataString(PyHierarchyCallData callData) {
    return callData.getCallerName()
           + "\t" + callData.getCalleeFile()
           + "\t" + callData.getCalleeName()
           + "\t" + StringUtil.join(callData.getCalleeCallLines(), "\t");
  }

}
