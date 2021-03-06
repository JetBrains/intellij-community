package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.api.model.V2Repository
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule

data class PackageSearchRepository(
  @NlsSafe val id: String?,
  @NlsSafe val name: String?,
  @NlsSafe val url: String?,
  val projectModule: ProjectModule?,
  val remoteInfo: V2Repository?
)
