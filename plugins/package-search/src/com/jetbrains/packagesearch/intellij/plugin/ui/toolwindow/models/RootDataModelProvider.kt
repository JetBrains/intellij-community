package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.IPropertyView

internal interface RootDataModelProvider {

    val project: Project

    val dataModelProperty: IPropertyView<RootDataModel>

    val statusProperty: IPropertyView<DataStatus>
}
