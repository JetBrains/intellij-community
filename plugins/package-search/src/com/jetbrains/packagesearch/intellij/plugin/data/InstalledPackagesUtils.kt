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

package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.extensibility.CoroutineProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.key
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.DependencyUsageInfo
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logTrace
import com.jetbrains.packagesearch.intellij.plugin.util.logWarn
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal suspend fun installedPackages(
    dependenciesByModule: Map<ProjectModule, List<ResolvedUnifiedDependency>>,
    project: Project,
    dataProvider: ProjectDataProvider,
    traceInfo: TraceInfo
): List<PackageModel.Installed> {
    val usageInfoByDependency = mutableMapOf<UnifiedDependency, MutableList<DependencyUsageInfo>>()
    logTrace(traceInfo) { "installedPackages started" }
    for (module in dependenciesByModule.keys) {
        dependenciesByModule[module]?.forEach { (dependency, resolvedVersion, declarationIndexInBuildFile) ->
            yield()
            val usageInfo = DependencyUsageInfo(
                projectModule = module,
                declaredVersion = PackageVersion.from(dependency.coordinates.version),
                resolvedVersion = PackageVersion.from(resolvedVersion),
                scope = PackageScope.from(dependency.scope),
                userDefinedScopes = module.moduleType.userDefinedScopes(project)
                    .map { rawScope -> PackageScope.from(rawScope) },
                declarationIndexInBuildFile = declarationIndexInBuildFile
            )
            val usageInfoList = usageInfoByDependency.getOrPut(dependency) { mutableListOf() }
            if (usageInfoByDependency.size % 100 == 0) logTrace(traceInfo) { "usageInfoByDependency.size = ${usageInfoByDependency.size}" }
            usageInfoList.add(usageInfo)
        }
    }
    logTrace(traceInfo) { "usageInfoByDependency.size = ${usageInfoByDependency.size}" }
    val installedDependencies = dependenciesByModule.values.flatten()
        .mapNotNull { InstalledDependency.from(it.dependency) }

    val dependencyRemoteInfoMap = dataProvider.fetchInfoFor(installedDependencies, traceInfo)
    logTrace(traceInfo) { "dependencyRemoteInfoMap.size = ${dependencyRemoteInfoMap.size}" }

    return usageInfoByDependency.parallelMap { (dependency, usageInfo) ->
        val installedDependency = InstalledDependency.from(dependency)
        val remoteInfo = if (installedDependency != null) {
            dependencyRemoteInfoMap[installedDependency]
        } else {
            null
        }

        PackageModel.fromInstalledDependency(
            unifiedDependency = dependency,
            usageInfo = usageInfo,
            remoteInfo = remoteInfo,
            normalizer = packageVersionNormalizer
        )
    }.filterNotNull().sortedBy { it.sortKey }
}

internal suspend fun fetchProjectDependencies(
    modules: List<ProjectModule>,
    cacheDirectory: Path,
    json: Json
) = coroutineScope {
    modules.associateWith { module -> async { module.installedDependencies(cacheDirectory, json) } }
        .mapValues { (_, value) -> value.await() }
}

internal suspend fun ProjectModule.installedDependencies(cacheDirectory: Path, json: Json) = coroutineScope {
    val fileHashCode = buildFile?.hashCode() ?: return@coroutineScope emptyList()

    val cacheFile = File(cacheDirectory.absolutePathString(), "$fileHashCode.json")

    if (!cacheFile.exists()) {
        withContext(Dispatchers.IO) {
            cacheFile.apply { parentFile.mkdirs() }.createNewFile()
        }
    }

    val sha256Deferred: Deferred<String> = ApplicationManager.getApplication().coroutineScope.async {
        StringUtil.toHexString(DigestUtil.sha256().digest(buildFile.contentsToByteArray()))
    }

    val cachedContents = runCatching { cacheFile.readText() }
        .onFailure { logWarn("installedDependencies", it) { "Unable to load caches from \"${cacheFile.absolutePath}\"" } }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    val cache = if (cachedContents != null) {
        runCatching { json.decodeFromString<InstalledDependenciesCache>(cachedContents) }
            .onFailure { logWarn("installedDependencies", it) { "Dependency JSON cache file read failed for ${buildFile.path}" } }
            .getOrNull()
    } else {
        null
    }

    val sha256 = sha256Deferred.await()
    if (
        cache?.sha256 == sha256
        && cache.fileHashCode == fileHashCode
        && cache.cacheVersion == PluginEnvironment.Caches.version
        // if dependencies are empty it could be because build APIs have previously failed
        && (cache.dependencies.isNotEmpty() || cache.parsingAttempts >= PluginEnvironment.Caches.maxAttempts)
    ) {
        return@coroutineScope cache.dependencies
    }
    val operationProvider = CoroutineProjectModuleOperationProvider.forProjectModuleType(moduleType)

    val declaredDependencies =
        runCatching { operationProvider?.declaredDependenciesInModule(this@installedDependencies) }
            .onFailure { logWarn("installedDependencies", it) { "Unable to list dependencies in module $name" } }
            .getOrNull()
            ?.toList()
            ?: emptyList()

    val scopes = declaredDependencies.mapNotNull { it.unifiedDependency.scope }.toSet()

    val resolvedDependenciesMapJob = async {
        runCatching {
            operationProvider?.resolvedDependenciesInModule(this@installedDependencies, scopes)
                ?.mapNotNull { dep -> dep.key?.let { it to dep.coordinates.version } }
                ?.toMap()
                ?: emptyMap()
        }.onFailure { logWarn("Error while evaluating resolvedDependenciesInModule for $name", it) }
            .getOrElse { emptyMap() }
    }

    val dependenciesLocationMap = declaredDependencies
        .mapNotNull { dependency ->
            dependencyDeclarationCallback(dependency).await()
                ?.let { location -> dependency to location }
        }
        .toMap()

    val resolvedDependenciesMap = resolvedDependenciesMapJob.await()

    val dependencies: List<ResolvedUnifiedDependency> = declaredDependencies.map {
        ResolvedUnifiedDependency(it.unifiedDependency, resolvedDependenciesMap[it.unifiedDependency.key], dependenciesLocationMap[it])
    }

    nativeModule.project.lifecycleScope.launch {
        val jsonText = json.encodeToString(
            value = InstalledDependenciesCache(
                cacheVersion = PluginEnvironment.Caches.version,
                fileHashCode = fileHashCode,
                sha256 = sha256,
                parsingAttempts = cache?.parsingAttempts?.let { it + 1 } ?: 1,
                projectName = name,
                dependencies = dependencies
            )
        )

        cacheFile.writeText(jsonText)
    }

    dependencies
}

@Serializable
internal data class InstalledDependenciesCache(
    val cacheVersion: Int,
    val fileHashCode: Int,
    val sha256: String,
    val parsingAttempts: Int = 0,
    val projectName: String,
    val dependencies: List<ResolvedUnifiedDependency>
)

@Serializable
data class ResolvedUnifiedDependency(
    val dependency: @Serializable(with = UnifiedDependencySerializer::class) UnifiedDependency,
    val resolvedVersion: String? = null,
    val declarationIndexes: DependencyDeclarationIndexes?
)

internal object UnifiedCoordinatesSerializer : KSerializer<UnifiedCoordinates> {

    override val descriptor = buildClassSerialDescriptor(UnifiedCoordinates::class.qualifiedName!!) {
        element<String>("groupId", isOptional = true)
        element<String>("artifactId", isOptional = true)
        element<String>("version", isOptional = true)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var groupId: String? = null
        var artifactId: String? = null
        var version: String? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> groupId = decodeStringElement(descriptor, 0)
                1 -> artifactId = decodeStringElement(descriptor, 1)
                2 -> version = decodeStringElement(descriptor, 2)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        UnifiedCoordinates(groupId, artifactId, version)
    }

    override fun serialize(encoder: Encoder, value: UnifiedCoordinates) = encoder.encodeStructure(descriptor) {
        value.groupId?.let { encodeStringElement(descriptor, 0, it) }
        value.artifactId?.let { encodeStringElement(descriptor, 1, it) }
        value.version?.let { encodeStringElement(descriptor, 2, it) }
    }
}

internal object UnifiedDependencySerializer : KSerializer<UnifiedDependency> {

    override val descriptor = buildClassSerialDescriptor(UnifiedDependency::class.qualifiedName!!) {
        element("coordinates", UnifiedCoordinatesSerializer.descriptor)
        element<String>("scope", isOptional = true)
    }

    override fun deserialize(decoder: Decoder) = decoder.decodeStructure(descriptor) {
        var coordinates: UnifiedCoordinates? = null
        var scope: String? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> coordinates = decodeSerializableElement(descriptor, 0, UnifiedCoordinatesSerializer)
                1 -> scope = decodeStringElement(descriptor, 1)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        UnifiedDependency(
            coordinates = requireNotNull(coordinates) { "coordinates property missing while deserializing ${UnifiedDependency::class.qualifiedName}" },
            scope = scope
        )
    }

    override fun serialize(encoder: Encoder, value: UnifiedDependency) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, UnifiedCoordinatesSerializer, value.coordinates)
        value.scope?.let { encodeStringElement(descriptor, 1, it) }
    }
}
