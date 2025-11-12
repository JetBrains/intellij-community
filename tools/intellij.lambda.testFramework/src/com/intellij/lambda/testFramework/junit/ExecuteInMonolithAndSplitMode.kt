package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.junit5.RemoteDevRun
import com.intellij.util.SystemProperties
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/** List of available IDE modes (monolith, split) */
enum class IdeRunMode {
  MONOLITH, SPLIT
}

/**
 * Test will be executed in IDE in [IdeRunMode]
 * and tests will be grouped by [IdeRunMode] during the run to optimize time spent on IDE instance/application reinitialization
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
annotation class ExecuteInMonolithAndSplitMode(vararg val mode: IdeRunMode = [IdeRunMode.MONOLITH, IdeRunMode.SPLIT])


internal val isGroupedExecutionEnabled: Boolean = SystemProperties.getBooleanProperty("idea.test.grouped.execution", false)