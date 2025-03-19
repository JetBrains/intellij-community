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