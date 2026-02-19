// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core

import java.nio.file.Paths

object Defaults {
    val SRC_DIR = Paths.get("src")
    val KOTLIN_DIR = Paths.get("kotlin")
    val RESOURCES_DIR = Paths.get("resources")
    val TEST_DIR = Paths.get("test")
    val TEST_RESOURCES_DIR = Paths.get("testResources")
}