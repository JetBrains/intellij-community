// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.Ref
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.fromSdk
import com.jetbrains.python.psi.resolve.resolveTopLevelMember
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.testing.pyTestFixtures.TEST_FIXTURE_DECORATOR_NAMES
import com.jetbrains.python.testing.isTestElement
import org.jetbrains.annotations.NotNull

class PyFixtureRequestTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(
    @NotNull param: PyNamedParameter,
    @NotNull func: PyFunction,
    @NotNull context: TypeEvalContext,
  ): Ref<PyType>? {
    if (param.name != "request") return null

    // Determine whether we are in a real pytest context
    val isFixture = func.decoratorList?.decorators?.any { decorator ->
      TEST_FIXTURE_DECORATOR_NAMES.any { name ->
        val short = name.substringAfterLast('.')
        decorator.name == name || decorator.name == short
      }
    } ?: false

    // Only non-fixture branch needs additional gating
    if (!isFixture && !isTestElement(func, context)) {
      return null
    }

    val sdk = PythonSdkUtil.findPythonSdk(func) ?: return null
    val resolveContext = fromSdk(func.project, sdk)

    val fixtureRequestQName = if (isFixture) {
      QualifiedName.fromDottedString("_pytest.fixtures.SubRequest")
    } else {
      QualifiedName.fromDottedString("_pytest.fixtures.TopRequest")
    }

    val fixtureRequestClass = resolveTopLevelMember(fixtureRequestQName, resolveContext) as? PyClass

    val finalClass = fixtureRequestClass
                     ?: PyBuiltinCache.getInstance(func).objectType?.pyClass
                     ?: return null

    return if (isFixture) {
      Ref.create(PyFixtureRequestType.createSubRequest(finalClass))
    } else {
      Ref.create(PyFixtureRequestType.createTopRequest(finalClass))
    }
  }
}
