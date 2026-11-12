// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.codeInsight.PyDataclassParametersProvider;
import com.jetbrains.python.codeInsight.PyDataclassesKt;
import com.jetbrains.python.codeInsight.PyDataclassParameters;
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTransformType;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Locks the one-way module boundary: PyCharm Community does not ship the Ultimate-only {@code intellij.python.pydantic}
 * module, and therefore its {@code Pythonid.pyDataclassParametersProvider} extension is absent from this test module's
 * classpath. These tests assert the graceful-degradation contract for that configuration — Pydantic classes must remain
 * usable via their generic {@code dataclass_transform} shape rather than the (unavailable) Pydantic-specific handling.
 */
@Subsystems.Typing
@Layers.Functional
public class PyDataclassDegradationTest extends PyTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionPointName<PyDataclassParametersProvider> ep =
      ExtensionPointName.create("Pythonid.pyDataclassParametersProvider");
    ExtensionTestUtil.maskExtensions(
      ep,
      ContainerUtil.filter(ep.getExtensionList(), provider -> !provider.getClass().getName().startsWith("com.intellij.python.pydantic")),
      getTestRootDisposable());
  }

  // Without the pydantic module, a `pydantic.BaseModel` subclass is still recognized, but only as a generic
  // `dataclass_transform` class (via its metaclass) — no framework resolver claims it.
  public void testBaseModelSubclassDegradesToGenericDataclassTransform() {
    myFixture.copyDirectoryToProject("stubs/pydantic", "pydantic");
    PyFile file = (PyFile)myFixture.configureByText("a.py", """
      from pydantic import BaseModel

      class M(BaseModel):
          x: int
      """);
    PyClass cls = file.findTopLevelClass("M");
    assertNotNull(cls);
    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), file);

    PyDataclassParameters params = PyDataclassesKt.parseDataclassParameters(cls, context);
    assertNotNull("BaseModel subclass should still be recognized as a dataclass_transform class", params);
    assertTrue("Type should be dataclass_transform-based", params.getType().isDataclassTransformBased());
    assertEquals(PyDataclassTransformType.INSTANCE,
                 PyDataclassesKt.getDataclassKind(cls, context));
  }

  // Without the pydantic module, `@pydantic.dataclasses.dataclass` degrades to a generic `dataclass_transform` decorator
  // (its stub carries the PEP 681 marker);
  public void testPydanticDataclassDecoratorDegradesToGenericDataclassTransform() {
    myFixture.copyDirectoryToProject("stubs/pydantic", "pydantic");
    PyFile file = (PyFile)myFixture.configureByText("a.py", """
      from pydantic.dataclasses import dataclass

      @dataclass
      class C:
          x: int
      """);
    PyClass cls = file.findTopLevelClass("C");
    assertNotNull(cls);
    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), file);

    PyDataclassParameters params = PyDataclassesKt.parseDataclassParameters(cls, context);
    assertNotNull(params);
    assertTrue("Type should be dataclass_transform-based", params.getType().isDataclassTransformBased());
    assertEquals(PyDataclassTransformType.INSTANCE,
                 PyDataclassesKt.getDataclassKind(cls, context));
  }
}
