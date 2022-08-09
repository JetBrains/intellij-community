package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages.columns.renderers

import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import java.awt.Color

internal data class CellColors(val background: Color, val foreground: Color)

internal fun computeColors(isSelected: Boolean, isHover: Boolean, isSearchResult: Boolean): CellColors {
    val background = if (isSearchResult) {
        PackageSearchUI.Colors.PackagesTable.SearchResult.background(isSelected, isHover)
    } else {
        PackageSearchUI.Colors.PackagesTable.background(isSelected, isHover)
    }

    val foreground = if (isSearchResult) {
        PackageSearchUI.Colors.PackagesTable.SearchResult.foreground(isSelected, isHover)
    } else {
        PackageSearchUI.Colors.PackagesTable.foreground(isSelected, isHover)
    }

    return CellColors(background, foreground)
}
