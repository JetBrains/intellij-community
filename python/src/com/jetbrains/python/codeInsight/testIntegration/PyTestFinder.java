// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFinder;
import com.intellij.testIntegration.TestFinderHelper;
import com.intellij.util.ThreeState;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.testing.PythonUnitTestDetectorsBasedOnSettings;
import com.jetbrains.python.testing.doctest.PythonDocTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User : catherine
 */
public final class PyTestFinder implements TestFinder {
  @Override
  public PyDocStringOwner findSourceElement(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PyClass.class, PyFunction.class);
  }

  @NotNull
  @Override
  public Collection<PsiElement> findTestsForClass(@NotNull PsiElement element) {
    PyDocStringOwner source = findSourceElement(element);
    if (source == null) return Collections.emptySet();

    String sourceName = source.getName();
    if (sourceName == null) return Collections.emptySet();
    List<Pair<? extends PsiNamedElement, Integer>> classesWithProximities = new ArrayList<>();

    if (source instanceof PyClass) {
      Collection<String> names = PyClassNameIndex.allKeys(element.getProject());
      for (String eachName : names) {
        if (eachName.contains(sourceName)) {
          for (PyClass eachClass : PyClassNameIndex
            .find(eachName, element.getProject(), GlobalSearchScope.projectScope(element.getProject()))) {
            if (PythonUnitTestDetectorsBasedOnSettings.isTestClass(eachClass, ThreeState.UNSURE, null) || PythonDocTestUtil.isDocTestClass(eachClass)) {
              classesWithProximities.add(
                new Pair<PsiNamedElement, Integer>(eachClass, TestFinderHelper.calcTestNameProximity(sourceName, eachName)));
            }
          }
        }
      }
    }
    else {
      Collection<String> names = PyFunctionNameIndex.allKeys(element.getProject());
      for (String eachName : names) {
        if (eachName.contains(sourceName)) {
          for (PyFunction eachFunction : PyFunctionNameIndex
            .find(eachName, element.getProject(), GlobalSearchScope.projectScope(element.getProject()))) {
            if (PythonUnitTestDetectorsBasedOnSettings.isTestFunction(
              eachFunction, ThreeState.UNSURE, null) || PythonDocTestUtil.isDocTestFunction(eachFunction)) {
              classesWithProximities.add(
                new Pair<PsiNamedElement, Integer>(eachFunction, TestFinderHelper.calcTestNameProximity(sourceName, eachName)));
            }
          }
        }
      }
    }
    return TestFinderHelper.getSortedElements(classesWithProximities, true);
  }

  @NotNull
  @Override
  public Collection<PsiElement> findClassesForTest(@NotNull PsiElement element) {
    final PyFunction sourceFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    final PyClass source = PsiTreeUtil.getParentOfType(element, PyClass.class);
    if (sourceFunction == null && source == null) return Collections.emptySet();

    List<Pair<? extends PsiNamedElement, Integer>> testsWithWeights = new ArrayList<>();
    final List<Pair<String, Integer>> possibleNames = new ArrayList<>();
    if (source != null) {
      possibleNames.addAll(TestFinderHelper.collectPossibleClassNamesWithWeights(source.getName()));
    }
    if (sourceFunction != null) {
      possibleNames.addAll(TestFinderHelper.collectPossibleClassNamesWithWeights(sourceFunction.getName()));
    }

    for (final Pair<String, Integer> eachNameWithWeight : possibleNames) {
      for (PyClass eachClass : PyClassNameIndex.find(eachNameWithWeight.first, element.getProject(),
                                                     GlobalSearchScope.projectScope(element.getProject()))) {
        if (!PythonUnitTestDetectorsBasedOnSettings.isTestClass(eachClass, ThreeState.NO, null)) {
          testsWithWeights.add(new Pair<PsiNamedElement, Integer>(eachClass, eachNameWithWeight.second));
        }
      }
      for (PyFunction function : PyFunctionNameIndex.find(eachNameWithWeight.first, element.getProject(),
                                                          GlobalSearchScope.projectScope(element.getProject()))) {
        if (!PythonUnitTestDetectorsBasedOnSettings.isTestFunction(function, ThreeState.UNSURE, null)) {
          testsWithWeights.add(new Pair<PsiNamedElement, Integer>(function, eachNameWithWeight.second));
        }
      }
    }
    return TestFinderHelper.getSortedElements(testsWithWeights, false);
  }

  @Override
  public boolean isTest(@NotNull PsiElement element) {
    return PythonUnitTestDetectorsBasedOnSettings.isTestElement(element, null);
  }
}
