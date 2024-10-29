package com.jetbrains.performancePlugin.commands.dto

data class MoveFilesData(val files: List<String>, val toDirectory: String, val spanTag: String)
