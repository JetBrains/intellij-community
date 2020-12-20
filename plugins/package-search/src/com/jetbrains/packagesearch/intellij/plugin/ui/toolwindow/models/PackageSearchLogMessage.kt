package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import java.util.Date

enum class PackageSearchLogLevel {
    Debug,
    Verbose,
    Information,
    Minimal,
    Warning,
    Error
}

data class PackageSearchLogMessage(
    val timestamp: Date,
    val text: String,
    val level: PackageSearchLogLevel = PackageSearchLogLevel.Information
)
