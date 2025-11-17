// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.jetbrains.python.junit5.framework.annotations.InjectCodeInsightTestFixture
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext

internal class PyJUnit5CodeInsightFixtureInjector : BeforeEachCallback, Extension {

  override fun beforeEach(context: ExtensionContext) {
    val codeInsightFixture = context.getLookupFixtureManager().getRequired<CodeInsightTestFixture>()

    val testInstance = context.requiredTestInstance
    val fields = testInstance::class.java.declaredFields
      .filter { it.isAnnotationPresent(InjectCodeInsightTestFixture::class.java) }

    for (field in fields) {
      require(CodeInsightTestFixture::class.java.isAssignableFrom(field.type)) {
        "Field ${field.name} annotated with @InjectCodeInsightTestFixture is not of type CodeInsightTestFixture"
      }

      field.isAccessible = true
      field.set(testInstance, codeInsightFixture.get())
    }
  }
}