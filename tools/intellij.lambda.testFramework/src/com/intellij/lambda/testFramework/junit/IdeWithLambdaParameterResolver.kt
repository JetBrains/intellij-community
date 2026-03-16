package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class IdeWithLambdaParameterResolver : ParameterResolver {
  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type.isAssignableFrom(IdeWithLambda::class.java)

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return IdeInstance.ide
  }
}