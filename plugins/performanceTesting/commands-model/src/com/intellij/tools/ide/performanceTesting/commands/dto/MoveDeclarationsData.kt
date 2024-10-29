package com.intellij.tools.ide.performanceTesting.commands.dto

// Exactly the same class is in `intellij.performanceTesting` module, com.jetbrains.performancePlugin.commands.dto package.
// This is done the same to MoveFilesData classes, located in the very same packages.

// Effectively Move Declarations picks 1..n declarations from the same file and moves it to another file.
// So we need to know the source file, the set of declarations (names), and the target file.
data class MoveDeclarationsData(val fromFile: String, val declarations: List<String>, val toFile: String, val spanTag: String = "")
