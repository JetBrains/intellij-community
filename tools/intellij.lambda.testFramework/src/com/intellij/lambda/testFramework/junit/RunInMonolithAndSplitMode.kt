package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.driver.reused.ReusedRun
import com.intellij.lambda.testFramework.starter.LambdaReusedSessionStrategy
import org.junit.jupiter.api.ClassTemplate
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Test will be executed in IDE in the selected [IdeRunMode] values.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@ReusedRun(sessionStrategy = LambdaReusedSessionStrategy::class)
@ClassTemplate
@ExtendWith(
  TestFactoryLoggerSetter::class,
  MonolithAndSplitModeClassTemplateProvider::class,
  MonolithAndSplitModeInvocationInterceptor::class,
  IdeWithLambdaParameterResolver::class,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
annotation class RunInMonolithAndSplitMode(vararg val mode: IdeRunMode = [IdeRunMode.MONOLITH, IdeRunMode.SPLIT])
