// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinRunConfigurationMultiplatformUtils")
package org.jetbrains.kotlin.idea.run

import com.intellij.openapi.util.registry.Registry

fun forceGradleRunnerInMPP() = Registry.`is`("kotlin.mpp.tests.force.gradle", true)