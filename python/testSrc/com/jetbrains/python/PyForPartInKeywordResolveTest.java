// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyLightProjectDescriptor;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;

public class PyForPartInKeywordResolveTest extends PyResolveTestCase {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/keywords/forPart";
  }

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new PyLightProjectDescriptor(LanguageLevel.getLatest());
  }

  @Override
  protected PsiElement doResolve() {
    String fileBase = getTestName(true);
    myFixture.configureByFile(fileBase + ".py");
    return PyResolveTestCase.findReferenceByMarker(myFixture.getFile()).resolve();
  }


  public void testPositiveResolve() {
    assertResolvesTo(PyFunction.class, "__iter__");
    PsiReference ref = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
    String fullText = ref.getElement().getText();
    String rangeText = ref.getRangeInElement().substring(fullText);
    assertEquals("in", rangeText);
  }

  public void testBuiltinListResolve() {
    assertResolvesTo(PyFunction.class, "__iter__");
  }

  public void testAiterResolve() {
    assertResolvesTo(PyFunction.class, "__aiter__");
  }

  public void testOnlyGetItemUnresolved() {
    assertResolvesTo(PyFunction.class, "__getitem__");
  }

  public void testUnknownUnresolved() {
    assertUnresolved();
  }
}
