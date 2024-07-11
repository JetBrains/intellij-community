// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.env

import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.junit5.env.impl.PythonEnvExtension
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Python env test case that supports [PythonBinaryPath] etc
 * Example:
 * ```kotlin
@PyEnvTestCase
class PyEnvTestExample
 * ```
 */
@TestApplication
@Target(AnnotationTarget.CLASS)
@ExtendWith(PythonEnvExtension::class)
annotation class PyEnvTestCase