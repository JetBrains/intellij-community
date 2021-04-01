package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.IPropertyView

internal interface RootDataProvider {

    val project: Project

    val data: IPropertyView<RootData>

    val status: IPropertyView<DataStatus>
}
