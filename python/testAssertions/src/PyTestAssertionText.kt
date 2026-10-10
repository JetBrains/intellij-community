// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

/**
 * Marks a parameter that takes a test text with assertions.
 *
 * The DevKit editor support checks the assertions of a Python string when the string goes to such a parameter.
 * The string can also go to a parameter of a test method that gives it to such a parameter.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class PyTestAssertionText
