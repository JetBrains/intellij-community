// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.text.nullize
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.name


internal fun createCatalog(name: String, location: String): MavenCatalog? {
  if (MavenCatalogManager.isLocal(location)) {
    val path = getPathOrNull(location) ?: return null
    return MavenCatalog.Local(name, path)
  }
  else {
    val url = getUrlOrNull(location) ?: return null
    return MavenCatalog.Remote(name, url)
  }
}

internal fun getPathOrError(location: String) = runCatching { Path(FileUtil.expandUserHome(location)) }
internal fun getUrlOrError(location: String) = runCatching { URL(location) }

internal fun getPathOrNull(location: String) = getPathOrError(location).getOrNull()
internal fun getUrlOrNull(location: String) = getUrlOrError(location).getOrNull()

internal fun suggestCatalogNameByLocation(location: String): String {
  if (MavenCatalogManager.isLocal(location)) {
    return getPathOrNull(location)?.name?.nullize() ?: location
  }
  else {
    return getUrlOrNull(location)?.host?.nullize() ?: location
  }
}

internal fun ValidationInfoBuilder.validateCatalogLocation(location: String): ValidationInfo? {
  if (location.isEmpty()) {
    return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.empty"))
  }
  if (MavenCatalogManager.isLocal(location)) {
    return validateLocalLocation(location)
  }
  else {
    return validateRemoteLocation(location)
  }
}

private fun ValidationInfoBuilder.validateLocalLocation(location: String): ValidationInfo? {
  val pathOrError = getPathOrError(location)
  val exception = pathOrError.exceptionOrNull()
  if (exception != null) {
    val message = exception.message
    return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.invalid", message))
  }
  val path = pathOrError.getOrThrow()
  if (!path.exists()) {
    return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.not.exists"))
  }
  return null
}

private fun ValidationInfoBuilder.validateRemoteLocation(location: String): ValidationInfo? {
  val exception = getUrlOrError(location).exceptionOrNull()
  if (exception != null) {
    val message = exception.message
    return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.invalid", message))
  }
  return null
}