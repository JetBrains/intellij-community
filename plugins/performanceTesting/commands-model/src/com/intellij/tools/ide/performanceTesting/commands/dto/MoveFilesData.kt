package com.intellij.tools.ide.performanceTesting.commands.dto

data class MoveFilesData(val files: List<String>, val toDirectory: String, val spanTag: String = "")