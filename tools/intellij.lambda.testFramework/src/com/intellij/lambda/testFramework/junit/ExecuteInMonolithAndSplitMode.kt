package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.junit5.RemoteDevRun
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/** List of available IDE modes (monolith, split) */
enum class IdeRunMode {
  MONOLITH, SPLIT
}

/**
 * Test will be executed in IDE in [IdeRunMode]
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@ExtendWith(
  //TestApplicationExtension::class,
  MonolithAndSplitModeContextProvider::class,
  MonolithAndSplitModeInvocationInterceptor::class,
  RemoteDevRun::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@GroupTestsByMode
annotation class ExecuteInMonolithAndSplitMode(vararg val mode: IdeRunMode = [IdeRunMode.MONOLITH, IdeRunMode.SPLIT])