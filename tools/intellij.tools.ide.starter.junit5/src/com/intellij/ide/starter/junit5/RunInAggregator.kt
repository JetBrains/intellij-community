package com.intellij.ide.starter.junit5

import org.junit.jupiter.api.Tag
import java.lang.annotation.Inherited

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("RunInAggregator")
@Inherited
annotation class RunInAggregator
