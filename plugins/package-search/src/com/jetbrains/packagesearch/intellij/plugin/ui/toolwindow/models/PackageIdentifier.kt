package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.util.NlsSafe

@JvmInline
internal value class PackageIdentifier(@NlsSafe val rawValue: String)
