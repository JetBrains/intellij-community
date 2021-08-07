package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo

internal data class PackagesTableDataModel(
    val packages: List<PackageModel>,
    val onlyStable: Boolean,
    val targetModules: TargetModules,
    val traceInfo: TraceInfo
)
