package com.intellij.python.junit5Tests.framework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.TestOnly


/**
 * [kotlinx.coroutines.CoroutineScope] that is limited by [com.intellij.openapi.application.Application]
 */
@TestOnly
fun applicationScope(): TestFixture<CoroutineScope> = testFixture {
  @Service
  class MyService(val scope: CoroutineScope)

  val scope = ApplicationManager.getApplication().service<MyService>().scope
  initialized(scope) {
    scope.cancel()
  }
}

