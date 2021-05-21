// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.ide.konan

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.PlatformAnalysisParameters
import org.jetbrains.kotlin.analyzer.ResolverForModuleFactory
import org.jetbrains.kotlin.analyzer.ResolverForProject
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.functions.functionInterfacePackageFragmentProvider
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.caches.resolve.IdePlatformKindResolution
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.konan.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.descriptors.konan.KlibModuleOrigin
import org.jetbrains.kotlin.ide.konan.CommonizerNativeTargetsCompat.commonizerNativeTargetsCompat
import org.jetbrains.kotlin.ide.konan.analyzer.NativeResolverForModuleFactory
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.LibraryInfo
import org.jetbrains.kotlin.idea.caches.project.SdkInfo
import org.jetbrains.kotlin.idea.caches.project.lazyClosure
import org.jetbrains.kotlin.idea.caches.resolve.BuiltInsCacheKey
import org.jetbrains.kotlin.idea.compiler.IDELanguageSettingsProvider
import org.jetbrains.kotlin.idea.klib.AbstractKlibLibraryInfo
import org.jetbrains.kotlin.idea.klib.createKlibPackageFragmentProvider
import org.jetbrains.kotlin.idea.klib.isKlibLibraryRootForPlatform
import org.jetbrains.kotlin.idea.klib.safeRead
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.konan.properties.propertyList
import org.jetbrains.kotlin.konan.util.KlibMetadataFactories
import org.jetbrains.kotlin.library.BaseKotlinLibrary
import org.jetbrains.kotlin.library.KLIB_PROPERTY_NATIVE_TARGETS
import org.jetbrains.kotlin.library.isInterop
import org.jetbrains.kotlin.library.metadata.NullFlexibleTypeDeserializer
import org.jetbrains.kotlin.library.nativeTargets
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms.nativePlatformByTargetNames
import org.jetbrains.kotlin.resolve.ImplicitIntegerCoercion
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.serialization.konan.impl.KlibMetadataModuleDescriptorFactoryImpl
import org.jetbrains.kotlin.storage.StorageManager

class NativePlatformKindResolution : IdePlatformKindResolution {

    override fun createLibraryInfo(project: Project, library: Library): List<LibraryInfo> {
        return library.getFiles(OrderRootType.CLASSES).mapNotNull { file ->
            if (!isLibraryFileForPlatform(file)) return@createLibraryInfo emptyList()
            val path = PathUtil.getLocalPath(file) ?: return@createLibraryInfo emptyList()
            NativeKlibLibraryInfo(project, library, path)
        }
    }

    override fun createKlibPackageFragmentProvider(
        moduleInfo: ModuleInfo,
        storageManager: StorageManager,
        languageVersionSettings: LanguageVersionSettings,
        moduleDescriptor: ModuleDescriptor
    ): PackageFragmentProvider? {
        return (moduleInfo as? NativeKlibLibraryInfo)
            ?.resolvedKotlinLibrary
            ?.createKlibPackageFragmentProvider(
                storageManager = storageManager,
                metadataModuleDescriptorFactory = metadataFactories.DefaultDeserializedDescriptorFactory,
                languageVersionSettings = languageVersionSettings,
                moduleDescriptor = moduleDescriptor,
                lookupTracker = LookupTracker.DO_NOTHING
            )
    }

    override fun isLibraryFileForPlatform(virtualFile: VirtualFile): Boolean =
        virtualFile.isKlibLibraryRootForPlatform(NativePlatforms.unspecifiedNativePlatform)

    override fun createResolverForModuleFactory(
        settings: PlatformAnalysisParameters,
        environment: TargetEnvironment,
        platform: TargetPlatform
    ): ResolverForModuleFactory {
        return NativeResolverForModuleFactory(settings, environment, platform)
    }

    override val libraryKind: PersistentLibraryKind<*>?
        get() = NativeLibraryKind

    override val kind get() = NativeIdePlatformKind

    override fun getKeyForBuiltIns(moduleInfo: ModuleInfo, sdkInfo: SdkInfo?, stdlibInfo: LibraryInfo?): BuiltInsCacheKey = NativeBuiltInsCacheKey

    override fun createBuiltIns(
        moduleInfo: IdeaModuleInfo,
        projectContext: ProjectContext,
        resolverForProject: ResolverForProject<IdeaModuleInfo>,
        sdkDependency: SdkInfo?,
        stdlibDependency: LibraryInfo?,
    ) = createKotlinNativeBuiltIns(moduleInfo, projectContext)

    private fun createKotlinNativeBuiltIns(moduleInfo: ModuleInfo, projectContext: ProjectContext): KotlinBuiltIns {
        val stdlibInfo = moduleInfo.findNativeStdlib() ?: return DefaultBuiltIns.Instance

        val project = projectContext.project
        val storageManager = projectContext.storageManager

        val builtInsModule = metadataFactories.DefaultDescriptorFactory.createDescriptorAndNewBuiltIns(
            KotlinBuiltIns.BUILTINS_MODULE_NAME,
            storageManager,
            DeserializedKlibModuleOrigin(stdlibInfo.resolvedKotlinLibrary),
            stdlibInfo.capabilities
        )

        val languageVersionSettings = IDELanguageSettingsProvider.getLanguageVersionSettings(
            stdlibInfo,
            project
        )

        val stdlibPackageFragmentProvider = createKlibPackageFragmentProvider(
            stdlibInfo,
            storageManager,
            languageVersionSettings,
            builtInsModule
        ) ?: return DefaultBuiltIns.Instance

        builtInsModule.initialize(
            CompositePackageFragmentProvider(
                listOf(
                    stdlibPackageFragmentProvider,
                    functionInterfacePackageFragmentProvider(storageManager, builtInsModule),
                    (metadataFactories.DefaultDeserializedDescriptorFactory as KlibMetadataModuleDescriptorFactoryImpl)
                        .createForwardDeclarationHackPackagePartProvider(storageManager, builtInsModule)
                ),
                "CompositeProvider@NativeBuiltins for $builtInsModule"
            )
        )

        builtInsModule.setDependencies(listOf(builtInsModule))

        return builtInsModule.builtIns
    }

    object NativeBuiltInsCacheKey : BuiltInsCacheKey

    companion object {
        private val metadataFactories = KlibMetadataFactories(::KonanBuiltIns, NullFlexibleTypeDeserializer)

        private fun ModuleInfo.findNativeStdlib(): NativeKlibLibraryInfo? =
            dependencies().lazyClosure { it.dependencies() }
                .filterIsInstance<NativeKlibLibraryInfo>()
                .firstOrNull { it.isStdlib && it.compatibilityInfo.isCompatible }
    }
}

class NativeKlibLibraryInfo(project: Project, library: Library, libraryRoot: String) :
    AbstractKlibLibraryInfo(project, library, libraryRoot) {

    // If you're changing this, please take a look at ideaModelDependencies as well
    val isStdlib: Boolean get() = libraryRoot.endsWith(KONAN_STDLIB_NAME)

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() {
            val capabilities = super.capabilities.toMutableMap()
            capabilities += KlibModuleOrigin.CAPABILITY to DeserializedKlibModuleOrigin(resolvedKotlinLibrary)
            capabilities += ImplicitIntegerCoercion.MODULE_CAPABILITY to resolvedKotlinLibrary.safeRead(false) { isInterop }
            return capabilities
        }

    override val platform: TargetPlatform by lazy {
        val targetNames = resolvedKotlinLibrary.safeRead(null) { commonizerNativeTargetsCompat }
            ?: resolvedKotlinLibrary.safeRead(emptyList()) { nativeTargets }

        nativePlatformByTargetNames(targetNames)
    }
}

/**
 * Provides forward compatibility to klib's 'commonizer_native_targets' property (which is expected in 1.5.20)
 */
@Suppress("SpellCheckingInspection")
private object CommonizerNativeTargetsCompat {
    /**
     * Similar to [KLIB_PROPERTY_NATIVE_TARGETS] but this will also preserve targets
     * that were unsupported on the host creating this artifact
     */
    private const val KLIB_PROPERTY_COMMONIZER_NATIVE_TARGETS = "commonizer_native_targets"

    /**
     * Accessor for 'commonizer_native_targets' manifest property.
     * Can be removed once bundled compiler reaches 1.5.20
     */
    val BaseKotlinLibrary.commonizerNativeTargetsCompat: List<String>?
        get() = if (manifestProperties.containsKey(KLIB_PROPERTY_COMMONIZER_NATIVE_TARGETS))
            manifestProperties.propertyList(KLIB_PROPERTY_COMMONIZER_NATIVE_TARGETS)
        else null
}