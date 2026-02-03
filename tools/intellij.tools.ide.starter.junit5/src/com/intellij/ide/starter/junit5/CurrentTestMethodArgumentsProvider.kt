package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.kodein.di.direct
import org.kodein.di.instance
import java.lang.reflect.Method

/**
 * Enriches [CurrentTestMethod] with method's arguments
 */
open class CurrentTestMethodArgumentsProvider : InvocationInterceptor {
  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ) {
    intercept<Void?>(invocation, invocationContext)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ) {
    intercept<Void?>(invocation, invocationContext)
  }

  override fun <T> interceptTestFactoryMethod(
    invocation: InvocationInterceptor.Invocation<T?>, invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ): T? {
    return intercept<T?>(invocation, invocationContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method?>,
    extensionContext: ExtensionContext?,
  ) {
    intercept<Void?>(invocation, invocationContext)
  }

  private fun <T> intercept(invocation: InvocationInterceptor.Invocation<T?>, invocationContext: ReflectiveInvocationContext<Method?>): T? {
    // filter well-known JUnit5 arguments types that don't hold actual test params
    di.direct.instance<CurrentTestMethod>().get()?.arguments = invocationContext.arguments.filterNot { it is TestInfo || it is TestReporter }

    // Always proceed with the actual test method invocation.
    return invocation.proceed()
  }
}


