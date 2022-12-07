/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageSearchModule
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.asInstalledDependencyOrNull
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import org.apache.commons.lang3.StringUtils
import java.nio.file.Path

internal fun looksLikeGradleVariable(version: NormalizedPackageVersion<*>) = version.versionName.startsWith("$")

@NlsSafe
internal fun @receiver:NlsSafe String?.normalizeWhitespace() = StringUtils.normalizeSpace(this)

internal fun String?.nullIfBlank(): String? = if (isNullOrBlank()) null else this

fun VirtualFile.toNioPathOrNull(): Path? = fileSystem.getNioPath(this)

internal fun Map<PackageSearchModule, PackageSearchModule.Dependencies>.getInstalledDependencies() =
    asSequence().flatMap { it.value.declared.mapNotNull { it.asInstalledDependencyOrNull() } }.toSet()