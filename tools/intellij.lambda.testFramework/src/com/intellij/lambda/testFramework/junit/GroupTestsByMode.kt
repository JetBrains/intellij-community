package com.intellij.lambda.testFramework.junit

/** Group tests by [IdeRunMode] during the run
 *  to optimize time spent on IDE instance/application reinitialization */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GroupTestsByMode