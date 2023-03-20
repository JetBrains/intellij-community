// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit3Framework
import com.intellij.execution.junit.JUnit4Framework
import com.intellij.execution.junit.JUnit5Framework
import org.jetbrains.kotlin.idea.junit.framework.JUnit3KotlinTestFramework
import org.jetbrains.kotlin.idea.junit.framework.JUnit4KotlinTestFramework
import org.jetbrains.kotlin.idea.junit.framework.JUnit5KotlinTestFramework

class KotlinJUnit3FrameworkAdapter: AbstractKotlinTestFrameworkAdapter(JUnit3KotlinTestFramework::class.java, JUnit3Framework::class.java)

class KotlinJUnit4FrameworkAdapter: AbstractKotlinTestFrameworkAdapter(JUnit4KotlinTestFramework::class.java, JUnit4Framework::class.java)

class KotlinJUnit5FrameworkAdapter: AbstractKotlinTestFrameworkAdapter(JUnit5KotlinTestFramework::class.java, JUnit5Framework::class.java)