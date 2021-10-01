package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow

internal interface RootDataModelProvider {

    val project: Project

    val dataModelFlow: Flow<RootDataModel>

    val dataStatusState: Flow<DataStatus>
}
