package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.ConfigureCoroutineCancellationTimeout
import com.intellij.lambda.testFramework.starter.IdeConfigReset
import com.intellij.util.SystemProperties
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Test will be executed in IDE in [IdeRunMode]
 * and tests will be grouped by [IdeRunMode] during the run to optimize time spent on IDE instance/application reinitialization
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@ExtendWith(
  TestFactoryLoggerSetter::class,
  StartIdeBeforeEachCallback::class,
  IdeConfigReset::class,
  ConfigureCoroutineCancellationTimeout::class,
  MonolithAndSplitModeTestTemplateProvider::class,
  MonolithAndSplitModeInvocationInterceptor::class,
  BackgroundLambdaDefaultCallbacks::class,
  IdeWithLambdaParameterResolver::class,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
annotation class RunInMonolithAndSplitMode(vararg val mode: IdeRunMode = [IdeRunMode.MONOLITH, IdeRunMode.SPLIT])


internal val isGroupedExecutionEnabled: Boolean = SystemProperties.getBooleanProperty("idea.test.grouped.execution", true)