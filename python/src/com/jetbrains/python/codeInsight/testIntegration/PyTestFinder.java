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
import com.jetbrains.python.testing.PythonUnitTestUtil;
import com.jetbrains.python.testing.doctest.PythonDocTestUtil;
import com.jetbrains.python.testing.pytest.PyTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User : catherine
 */
public class PyTestFinder implements TestFinder {
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
          for (PyClass eachClass : PyClassNameIndex.find(eachName, element.getProject(), GlobalSearchScope.projectScope(element.getProject()))) {
            if (PythonUnitTestUtil.isTestClass(eachClass, ThreeState.UNSURE, null) || PythonDocTestUtil.isDocTestClass(eachClass)) {
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
          for (PyFunction eachFunction : PyFunctionNameIndex.find(eachName, element.getProject(), GlobalSearchScope.projectScope(element.getProject()))) {
            if (PythonUnitTestUtil.isTestFunction(
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

    List<Pair<? extends PsiNamedElement, Integer>> classesWithWeights = new ArrayList<>();
    final List<Pair<String, Integer>> possibleNames = new ArrayList<>();
    if (source != null)
      possibleNames.addAll(TestFinderHelper.collectPossibleClassNamesWithWeights(source.getName()));
    if (sourceFunction != null)
      possibleNames.addAll(TestFinderHelper.collectPossibleClassNamesWithWeights(sourceFunction.getName()));

    for (Pair<String, Integer> eachNameWithWeight : possibleNames) {
      for (PyClass eachClass : PyClassNameIndex.find(eachNameWithWeight.first, element.getProject(),
                                                     GlobalSearchScope.projectScope(element.getProject()))) {
        if (!PyTestUtil.isPyTestClass(eachClass, null))
          classesWithWeights.add(new Pair<PsiNamedElement, Integer>(eachClass, eachNameWithWeight.second));
      }
      for (PyFunction function : PyFunctionNameIndex.find(eachNameWithWeight.first, element.getProject(),
                                                           GlobalSearchScope.projectScope(element.getProject()))) {
        if (!PyTestUtil.isPyTestFunction(function))
          classesWithWeights.add(new Pair<PsiNamedElement, Integer>(function, eachNameWithWeight.second));
      }

    }
    return TestFinderHelper.getSortedElements(classesWithWeights, false);
  }

  @Override
  public boolean isTest(@NotNull PsiElement element) {
    PyClass cl = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    if (cl != null)
      return PyTestUtil.isPyTestClass(cl, null);
    return false;
  }
}
