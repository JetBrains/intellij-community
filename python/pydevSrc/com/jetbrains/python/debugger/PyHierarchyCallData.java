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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.Set;

public class PyHierarchyCallData {
  private final String myCallerFile;
  private String myCallerName;
  private final int myCallerDefLine;
  private final String myCalleeFile;
  private String myCalleeName;
  private final int myCalleeDefLine;
  private final Set<Integer> myCalleeCallLines = Sets.newHashSet();

  public PyHierarchyCallData(String callerFile, String callerName, int callerDefLine, String calleeFile, String calleeName, int calleeDefLine) {
    myCallerFile = callerFile;
    myCallerName = callerName;
    myCallerDefLine = callerDefLine;
    myCalleeFile = calleeFile;
    myCalleeName = calleeName;
    myCalleeDefLine = calleeDefLine;
  }

  public String getCallerFile() {
    return myCallerFile;
  }

  public String getCallerName() {
    return myCallerName;
  }

  public void setCallerName(String callerName) {
    myCallerName = callerName;
  }

  public int getCallerDefLine() {
    return myCallerDefLine;
  }

  public String getCalleeFile() {
    return myCalleeFile;
  }

  public String getCalleeName() {
    return myCalleeName;
  }

  public void setCalleeName(String calleeName) {
    myCalleeName = calleeName;
  }

  public int getCalleeDefLine() {
    return myCalleeDefLine;
  }

  public String toString() {
    return "Call info: \n"
           + "caller file: " + getCallerFile() + "\n"
           + "caller name: " + getCallerName() + "\n"
           + "caller def line: " + getCallerDefLine() + "\n"
           + "callee file: " + getCalleeFile() + "\n"
           + "callee name: " + getCalleeName() + "\n"
           + "callee def line: " + getCalleeDefLine() + "\n";
  }

  public void addCalleeCallLine(int i) {
    myCalleeCallLines.add(i);
  }

  public PyHierarchyCallData addAllCalleeCallLines(PyHierarchyCallData callData) {
    myCalleeCallLines.addAll(callData.getCalleeCallLines());

    return this;
  }

  public Set<Integer> getCalleeCallLines() {
    return myCalleeCallLines;
  }
}
