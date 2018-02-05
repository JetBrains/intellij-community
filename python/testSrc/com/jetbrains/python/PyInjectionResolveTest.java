/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.*;

/**
 * @author Mikhail Golubev
 */
public class PyInjectionResolveTest extends PyResolveTestCase {
  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    final PsiFile psiFile = myFixture.getFile();
    final int markerOffset = findMarkerOffset(psiFile);
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiElement injectedElement = manager.findInjectedElementAt(psiFile, markerOffset);
    assertNotNull("no injected element found at <ref> position", injectedElement);
    PsiReference reference = null;
    final PyReferenceOwner referenceOwner = PsiTreeUtil.getParentOfType(injectedElement, PyReferenceOwner.class);
    if (referenceOwner != null) {
      reference = referenceOwner.getReference();
    }
    assertNotNull("no reference found at <ref> position", reference);
    return reference.resolve();
  }


  // PY-20783
  public void testFStringFunctionParameter() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyParameter.class, "param");
  }
  
  // PY-20783
  public void testFStringLocalVariable() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }
  
   // PY-20783
  public void testFStringLocalVariableUnresolved() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertNull(doResolve()));
  }

  // PY-20783
  public void testFStringNestedScopes() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionTarget() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringComprehensionSourcePart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }
  
  // PY-21479
  public void testFStringNestedInResultComprehensionSourcePart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }
  
  // PY-21479
  public void testFStringComprehensionConditionPart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  // PY-21479
  public void testFStringNestedComprehensionSourcePart() {
    assertResolvesTo(LanguageLevel.PYTHON36, PyTargetExpression.class, "foo");
  }

  public void testTypeCommentReference() {
    assertResolvesTo(PyClass.class, "MyClass");
  }

  // PY-20863
  public void testQuotedTypeReferenceInsideClass() {
    assertResolvesTo(LanguageLevel.PYTHON34, PyClass.class, "MyClass");
  }
  
  // PY-20863
  public void testQuotedTypeReferenceInsideFunction() {
    assertResolvesTo(LanguageLevel.PYTHON34, PyClass.class, "MyClass");
  }
  
  // PY-20863
  public void testQuotedTypeReferenceTopLevel() {
    assertResolvesTo(LanguageLevel.PYTHON34, PyClass.class, "MyClass");
  }

  // PY-20377
  public void testFunctionTypeCommentParamTypeReference() {
    assertResolvesTo(PyClass.class, "MyClass");
  }

  // PY-20377
  public void testFunctionTypeCommentReturnTypeReference() {
    assertResolvesTo(PyClass.class, "MyClass");
  }

  // PY-22094
  public void testFStringInsideAssertStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> assertResolvesTo(PyParameter.class, "name"));
  }
}
