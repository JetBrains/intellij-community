// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyOverloadsProcessingPerformanceTest extends PyTestCase {
  public void testComputingResultTypeWithCodeAnalysisContext() {
    PyTargetExpression target = prepareTest();
    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertType("int", target, context);
  }

  @NotNull
  private PyTargetExpression prepareTest() {
    myFixture.copyDirectoryToProject("", "");
    myFixture.configureByFile("main.py");
    PyTargetExpression target = myFixture.findElementByText("expr", PyTargetExpression.class);
    assertNotNull(target);
    return target;
  }

  public void testOverloadsNotDuplicatedInMultiResolveResults() {
    PyTargetExpression expression = prepareTest();
    PyExpression value = expression.findAssignedValue();
    PyCallExpression call = assertInstanceOf(value, PyCallExpression.class);
    PyReferenceExpression refExpr = assertInstanceOf(call.getCallee(), PyReferenceExpression.class);
    ResolveResult[] resolveResults = refExpr.getReference().multiResolve(false);
    assertTrue("Too many resolve results " + resolveResults.length,
               resolveResults.length == 1000 || resolveResults.length == 1001);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/performance/overloads/";
  }

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }
}
