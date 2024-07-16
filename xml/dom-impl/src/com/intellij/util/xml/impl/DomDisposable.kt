// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service

/**
 * This service exists because we need to avoid registration of disposable children within the constructor of another disposable class.
 *
 * Sometimes, the cancellation within the constructor leads to non-disposing of children, as in
 * [this issue](https://youtrack.jetbrains.com/issue/IDEA-343397/Flaky-test-com.intellij.tests.BootstrapTests-LastInSuiteTest.testProjectLeakGroovy-Grails-Tests-2)
 *
 * By using a registered disposable, we can avoid this problem and properly dispose children of ill-constructed disposable.
 */
@Service(Service.Level.PROJECT)
class DomDisposable : Disposable {
  override fun dispose() {}
}