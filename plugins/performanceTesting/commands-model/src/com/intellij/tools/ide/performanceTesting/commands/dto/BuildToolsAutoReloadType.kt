// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands.dto

enum class BuildToolsAutoReloadType {
    /**
     * Reloads a project after any changes made to build script files
     */
    ALL,

    /**
     * Reloads a project after VCS updates and changes made to build script files outside the IDE
     */
    SELECTIVE,

    /**
     * Reloads a project only if cached data is corrupted, invalid or missing
     */
    NONE
  }