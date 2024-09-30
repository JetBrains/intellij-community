// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.PathUtil

fun getLibraryRootsWithIncompatibleAbi(module: Module): Collection<BinaryVersionedFile<BinaryVersion>> {
    val platform = module.platform

    val badRoots = when {
        platform.isJvm() -> getLibraryRootsWithIncompatibleAbiJvm(module)
        platform.isJs() -> getLibraryRootsWithIncompatibleAbiJavaScript(module)
        // TODO: also check it for Native KT-34525
        else -> return emptyList()
    }

    return if (badRoots.isEmpty()) emptyList() else badRoots.toHashSet()
}

fun getLibraryRootsWithIncompatibleAbiJvm(module: Module): Collection<BinaryVersionedFile<MetadataVersion>> {
    return getLibraryRootsWithAbiIncompatibleVersion(module, MetadataVersion.INSTANCE, KotlinJvmMetadataVersionIndex.NAME)
}

fun getLibraryRootsWithIncompatibleAbiJavaScript(module: Module): Collection<BinaryVersionedFile<JsMetadataVersion>> {
    return getLibraryRootsWithAbiIncompatibleVersion(module, JsMetadataVersion.INSTANCE, KotlinJsMetadataVersionIndex.NAME)
}

fun Project.forEachAllUsedLibraries(processor: (Library) -> Boolean) {
    OrderEnumerator.orderEntries(this).forEachLibrary(processor)
}

data class BinaryVersionedFile<out T : BinaryVersion>(val file: VirtualFile, val version: T, val supportedVersion: T)

private fun <T : BinaryVersion> getLibraryRootsWithAbiIncompatibleVersion(
    module: Module,
    supportedVersion: T,
    indexId: ID<T, *>
): Collection<BinaryVersionedFile<T>> {
    val moduleWithAllDependencies = setOf(module) + ModuleUtil.getAllDependentModules(module)
    val moduleWithAllDependentLibraries = GlobalSearchScope.union(
        moduleWithAllDependencies.map { it.moduleWithLibrariesScope }.toTypedArray()
    )

    val allVersions = FileBasedIndex.getInstance().getAllKeys(indexId, module.project)
    val badVersions = allVersions.filterNot(BinaryVersion::isCompatible).toHashSet()
    val badRoots = hashSetOf<BinaryVersionedFile<T>>()
    val fileIndex = ProjectFileIndex.getInstance(module.project)

    for (version in badVersions) {
        val indexedFiles = FileBasedIndex.getInstance().getContainingFiles(indexId, version, moduleWithAllDependentLibraries)
        for (indexedFile in indexedFiles) {
            val libraryRoot = fileIndex.getClassRootForFile(indexedFile) ?: error(
                "Only library roots were requested, and only class files should be indexed with the $indexId key. " +
                        "File: ${indexedFile.path}"
            )
            badRoots.add(BinaryVersionedFile(VfsUtil.getLocalFile(libraryRoot), version, supportedVersion))
        }
    }

    return badRoots
}

const val MAVEN_JS_STDLIB_ID = PathUtil.JS_LIB_NAME
const val MAVEN_JS_TEST_ID = PathUtil.KOTLIN_TEST_JS_NAME

data class LibInfo(
    val groupId: String,
    val name: String,
    val version: String = "0.0.0"
)

data class DeprecatedLibInfo(
    val old: LibInfo,
    val new: LibInfo,
    val outdatedAfterVersion: String,
    @Nls val message: String
)

private fun deprecatedLib(
    oldGroupId: String,
    oldName: String,
    newGroupId: String = oldGroupId,
    newName: String = oldName,
    outdatedAfterVersion: String,
    @Nls message: String
): DeprecatedLibInfo {
    return DeprecatedLibInfo(
        old = LibInfo(groupId = oldGroupId, name = oldName),
        new = LibInfo(groupId = newGroupId, name = newName),
        outdatedAfterVersion = outdatedAfterVersion,
        message = message
    )
}

val DEPRECATED_LIBRARIES_INFORMATION = listOf(
    deprecatedLib(
        oldGroupId = "org.jetbrains.kotlin",
        oldName = PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME, newName = PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME,
        outdatedAfterVersion = "1.2.0-rc-39",
        message = KotlinProjectConfigurationBundle.message(
            "version.message.is.deprecated.since.1.2.0.and.should.be.replaced.with",
            PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME,
            PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME
        )
    ),

    deprecatedLib(
        oldGroupId = "org.jetbrains.kotlin",
        oldName = PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME, newName = PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME,
        outdatedAfterVersion = "1.2.0-rc-39",
        message = KotlinProjectConfigurationBundle.message(
            "version.message.is.deprecated.since.1.2.0.and.should.be.replaced.with",
            PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME,
            PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME
        )
    )
)