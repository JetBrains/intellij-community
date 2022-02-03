package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.DigestUtil
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.DependencyUsageInfo
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.InstalledDependency
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ModuleModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackagesToUpgrade
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.ProjectDataProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.computeActionsAsync
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import com.jetbrains.packagesearch.intellij.plugin.util.packageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.parallelMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    dependenciesByModule: Map<ProjectModule, List<UnifiedDependency>>,
    project: Project,
    dataProvider: ProjectDataProvider,
    traceInfo: TraceInfo
): List<PackageModel.Installed> {
    val usageInfoByDependency = mutableMapOf<UnifiedDependency, MutableList<DependencyUsageInfo>>()
    for (module in dependenciesByModule.keys) {
        dependenciesByModule[module]?.forEach { dependency ->
            yield()
            // Skip packages we don't know the version for
            val rawVersion = dependency.coordinates.version

            val usageInfo = DependencyUsageInfo(
                projectModule = module,
                version = PackageVersion.from(rawVersion),
                scope = PackageScope.from(dependency.scope),
                userDefinedScopes = module.moduleType.userDefinedScopes(project)
                    .map { rawScope -> PackageScope.from(rawScope) }
            )
            val usageInfoList = usageInfoByDependency.getOrPut(dependency) { mutableListOf() }
            usageInfoList.add(usageInfo)
        }
    }

    val installedDependencies = dependenciesByModule.values.flatten()
        .mapNotNull { InstalledDependency.from(it) }

    val dependencyRemoteInfoMap = dataProvider.fetchInfoFor(installedDependencies, traceInfo)

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
): Map<ProjectModule, List<UnifiedDependency>> =
    coroutineScope {
        modules.associateWith { module -> async { module.installedDependencies(cacheDirectory, json) } }
            .mapValues { (_, value) -> value.await() }
    }

@Suppress("BlockingMethodInNonBlockingContext")
internal suspend fun ProjectModule.installedDependencies(cacheDirectory: Path, json: Json): List<UnifiedDependency> = coroutineScope {
    val fileHashCode = buildFile.hashCode()

    val cacheFile = File(cacheDirectory.absolutePathString(), "$fileHashCode.json")

    if (!cacheFile.exists()) withContext(Dispatchers.IO) {
        cacheFile.apply { parentFile.mkdirs() }.createNewFile()
    }

    val sha256Deferred: Deferred<String> = async(AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
        StringUtil.toHexString(DigestUtil.sha256().digest(buildFile.contentsToByteArray()))
    }

    val cachedContents = withContext(Dispatchers.IO) { kotlin.runCatching { cacheFile.readText() } }
        .onFailure { logDebug("installedDependencies", it) { "Someone messed with our cache file UGH ${cacheFile.absolutePath}" } }
        .getOrNull()?.takeIf { it.isNotBlank() }

    val cache = if (cachedContents != null) {
        // TODO: consider invalidating when ancillary files change (e.g., gradle.properties)
        runCatching { json.decodeFromString<InstalledDependenciesCache>(cachedContents) }
            .onFailure { logDebug("installedDependencies", it) { "Dependency JSON cache file read failed for ${buildFile.path}" } }
            .getOrNull()
    } else {
        null
    }

    val sha256 = sha256Deferred.await()
    if (cache?.sha256 == sha256 && cache.fileHashCode == fileHashCode && cache.cacheVersion == PluginEnvironment.cachesVersion) {
        return@coroutineScope cache.dependencies
    }

    val dependencies =
        readAction {
            runCatching {
                ProjectModuleOperationProvider.forProjectModuleType(moduleType)
                    ?.listDependenciesInModule(this@installedDependencies)
            }
        }
            .onFailure { logDebug("installedDependencies", it) { "Unable to list dependencies in module $name" } }
            .getOrNull()?.toList() ?: emptyList()

    nativeModule.project.lifecycleScope.launch {
        val jsonText = json.encodeToString(
            value = InstalledDependenciesCache(
                cacheVersion = PluginEnvironment.cachesVersion,
                fileHashCode = fileHashCode,
                sha256 = sha256,
                projectName = name,
                dependencies = dependencies
            )
        )

        withContext(AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
            cacheFile.writeText(jsonText)
        }
    }

    dependencies
}

@Serializable
internal data class InstalledDependenciesCache(
    val cacheVersion: Int,
    val fileHashCode: Int,
    val sha256: String,
    val projectName: String,
    val dependencies: List<@Serializable(with = UnifiedDependencySerializer::class) UnifiedDependency>
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

internal suspend fun generateOperationData(
    moduleModels: List<ModuleModel>,
    stable: PackagesToUpgrade,
    repos: KnownRepositories.All,
    onlyStable: Boolean,
    project: Project
) = moduleModels.associate { module ->
    module.projectModule.nativeModule to (stable.upgradesByModule[module.projectModule.nativeModule]?.parallelMap { packageUpgradeInfo ->
        val targetModule = TargetModules.from(module)
        val operations = computeActionsAsync(
            project, packageUpgradeInfo.packageModel, targetModule, repos.filterOnlyThoseUsedIn(targetModule), onlyStable
        )
        PackageSearchProjectService.AvailableUpdatesMap.OperationData(
            packageUpgradeInfo,
            operations,
            operations.primaryOperations.await()
        )
    } ?: emptyList())
}