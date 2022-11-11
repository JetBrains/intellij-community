package com.intellij.ide.starter.runner

import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

/**
 * Container, that pass around test method reference
 */
object CurrentTestMethod {
  private lateinit var testMethod: AtomicReference<Method>

  fun set(method: Method) {
    if (this::testMethod.isInitialized) {
      testMethod.set(method)
    }
    else {
      testMethod = AtomicReference(method)
    }
  }

  fun get(): Method? {
    return if (this::testMethod.isInitialized) {
      testMethod.get()
    }
    else null
  }
}