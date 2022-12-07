package com.jetbrains.packagesearch.intellij.plugin.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.appSystemDir
import com.intellij.util.io.delete
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.NormalizedPackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.versions.PackageVersionNormalizer
import com.jetbrains.packagesearch.intellij.plugin.util.CoroutineLRUCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal class PackageSearchCachesService : Disposable {

    private val normalizerCacheFile = appSystemDir.resolve("caches/pkgs/normalizedVersions.json")

    private val json = Json {
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    private val normalizerCache: CoroutineLRUCache<PackageVersion.Named, NormalizedPackageVersion<PackageVersion.Named>> =
// TODO compilation bug due to https://github.com/Kotlin/kotlinx.serialization/issues/1264
//      normalizerCacheFile.takeIf { it.exists() }
//            ?.runCatching {
//                json.decodeFromString(
//                  CoroutineLRUCache.serializer<PackageVersion.Named, NormalizedPackageVersion<PackageVersion.Named>>(),
//                  readText()
//                )
//            }
//            ?.getOrNull()
//      ?:
        CoroutineLRUCache(4_000)

    val normalizer = PackageVersionNormalizer(normalizerCache)

    override fun dispose() {
// TODO compilation bug due to https://github.com/Kotlin/kotlinx.serialization/issues/1264
//        normalizerCacheFile
//            .apply { if (!parent.exists()) Files.createDirectories(parent) }
//            .writeText(json.encodeToString(CoroutineLRUCache.serializer(), normalizerCache))
    }

    suspend fun clear() = coroutineScope {
        launch { normalizerCache.clear() }
        launch(Dispatchers.IO) { normalizerCacheFile.delete() }
    }
}