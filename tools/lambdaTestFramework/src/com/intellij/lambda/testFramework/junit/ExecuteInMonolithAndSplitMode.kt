package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.junit5.RemoteDevRun
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.annotation.Inherited

/**
 * Test will be executed in monolith and in split mode.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestTemplate
@Inherited
@ExtendWith(TestApplicationExtension::class,
            MonolithAndSplitModeContextProvider::class,
            MonolithAndSplitModeInvocationInterceptor::class,
            MonolithAndSplitModeIdeInstanceInitializer::class,
            RemoteDevRun::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
annotation class ExecuteInMonolithAndSplitMode(vararg val mode: LambdaRdIdeType = [LambdaRdIdeType.FRONTEND, LambdaRdIdeType.BACKEND, LambdaRdIdeType.MONOLITH])
