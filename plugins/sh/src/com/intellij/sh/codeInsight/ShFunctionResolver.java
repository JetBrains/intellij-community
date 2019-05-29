// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.ShGenericCommandDirective;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ShFunctionResolver {
  private final PsiFile myFile;
  private final PsiElement myResolvingElement;
  private final Map<String, FunctionInfo> myFunctionResolvingMap;

  public ShFunctionResolver(@NotNull PsiFile file, @NotNull PsiElement resolvingElement) {
    myFile = file;
    myResolvingElement = resolvingElement;
    myFunctionResolvingMap = new HashMap<>();
  }

  @Nullable
  public PsiElement resolveElement() {
    PsiElement[] children = myFile.getChildren();
    for (PsiElement child : children) {
      PsiElement target = collectStatistic(child);
      if (target != null) return target;
    }
    return resolveUnlinkedMethods();
  }

  private PsiElement resolveUnlinkedMethods() {
    while (containsUnlinkedMethods()) {
      for (Map.Entry<String, FunctionInfo> entry: myFunctionResolvingMap.entrySet()) {
        if (!entry.getValue().checked) {
          PsiElement target = collectFunctionStatistic(entry.getValue());
          if (target != null) return target;
        }
      }
    }
    return null;
  }

  private boolean containsUnlinkedMethods() {
    for (Map.Entry<String, FunctionInfo> entry: myFunctionResolvingMap.entrySet()) {
      if (!entry.getValue().checked) return true;
    }
    return false;
  }

  private PsiElement collectStatistic(PsiElement element) {
    if (element == null) return null;
    if (element instanceof ShFunctionDefinition) {
      ShFunctionDefinition functionDefinition = (ShFunctionDefinition)element;
      myFunctionResolvingMap.put(functionDefinition.getFunctionName().getText(), new FunctionInfo(functionDefinition));
      return null;
    }
    if (element instanceof ShGenericCommandDirective) {
      if (myFunctionResolvingMap.containsKey(element.getText())) {
        FunctionInfo functionInfo = myFunctionResolvingMap.get(element.getText());
        if (element == myResolvingElement) return functionInfo.function.getFunctionName();
        if (!functionInfo.checked) {
          PsiElement target = collectFunctionStatistic(functionInfo);
          if (target != null) return target;
        }
        functionInfo.references.add(element);
      }
    }
    for (PsiElement child : element.getChildren()) {
      PsiElement target = collectStatistic(child);
      if (target != null) return target;
    }
    return null;
  }

  private PsiElement collectFunctionStatistic(FunctionInfo functionInfo) {
    PsiElement[] children = functionInfo.function.getChildren();
    functionInfo.checked = true;
    for (PsiElement child : children) {
      PsiElement target = collectStatistic(child);
      if (target != null) return target;
    }
    return null;
  }

  class FunctionInfo {
    private final ShFunctionDefinition function;
    private final List<PsiElement> references;
    private boolean checked;

    private FunctionInfo(ShFunctionDefinition function) {
      this.function = function;
      references = new ArrayList<>();
    }
  }
}
