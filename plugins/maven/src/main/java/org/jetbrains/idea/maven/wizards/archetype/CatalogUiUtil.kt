// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.ui.validation.validationTextErrorFor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.text.nullize
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.indices.archetype.MavenCatalogManager
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

internal fun createCatalog(location: String): MavenCatalog? {
  if (location.isNotEmpty()) {
    val name = suggestCatalogNameByLocation(location)
    return createCatalog(name, location)
  }
  return null
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

val CHECK_MAVEN_CATALOG = validationTextErrorFor { location ->
  if (MavenCatalogManager.isLocal(location)) {
    validateLocalLocation(location)
  }
  else {
    validateRemoteLocation(location)
  }
}

private fun validateLocalLocation(location: String): String? {
  val pathOrError = getPathOrError(location)
  val exception = pathOrError.exceptionOrNull()
  if (exception != null) {
    val message = exception.message
    return MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.invalid", message)
  }
  val path = pathOrError.getOrThrow()
  if (!path.exists()) {
    return MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.not.exists")
  }
  return null
}

private fun validateRemoteLocation(location: String): String? {
  val exception = getUrlOrError(location).exceptionOrNull()
  if (exception != null) {
    val message = exception.message
    return MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.invalid", message)
  }
  return null
}