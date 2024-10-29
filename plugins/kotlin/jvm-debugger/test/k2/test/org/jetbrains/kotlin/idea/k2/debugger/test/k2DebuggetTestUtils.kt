// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.unregisterService
import org.jetbrains.kotlin.caches.resolve.*
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ide.konan.NativePlatformKindResolution
import org.jetbrains.kotlin.idea.caches.resolve.IdePackageOracleFactory
import org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.idea.caches.resolve.ResolveOptimizingOptionsProvider
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.stubindex.resolve.PluginDeclarationProviderFactoryService
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.DummyCodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.ResolutionAnchorProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService

/**
 * Needed for DebuggerTestCompilerFacility to be able to compile testdata, probably need to be rewritten to compile with K2 compiler
 */
internal inline fun <R> withTestServicesNeededForCodeCompilation(project: Project, action: () -> R): R {
    val disposable = Disposer.newCheckedDisposable("withTestServicesNeededForCodeCompilation")
    val services = listOf(
        ServiceWithImplementation(ScriptDependenciesModificationTracker::class.java) { ScriptDependenciesModificationTracker() },
        ServiceWithImplementation(KotlinCacheService::class.java, ::KotlinCacheServiceImpl),
        ServiceWithImplementation(ResolutionAnchorProvider::class.java) { DummyResolutionAnchorProvider() },
        ServiceWithImplementation(CodeAnalyzerInitializer::class.java) { DummyCodeAnalyzerInitializer(project) },
        ServiceWithImplementation(DeclarationProviderFactoryService::class.java) { PluginDeclarationProviderFactoryService() },
        ServiceWithImplementation(IdePackageOracleFactory::class.java, ::IdePackageOracleFactory)
    )

    if (IdePlatformKindResolution.getInstances().isEmpty()) {
        listOf(
            JvmPlatformKindResolution(),
            JsPlatformKindResolution(),
            WasmJsPlatformKindResolution(),
            WasmWasiPlatformKindResolution(),
            NativePlatformKindResolution(),
            CommonPlatformKindResolution(),
        ).forEach { IdePlatformKindResolution.registerExtension(it, disposable) }
    }

    services.forEach { (serviceInterface, createServiceInstance) ->
        val serviceInstance = createServiceInstance(project)
        (serviceInstance as? Disposable)?.let { Disposer.register(disposable, it) }
        project.registerServiceInstance(serviceInterface as Class<Any>, serviceInstance)
    }

    val extensionArea = ApplicationManager.getApplication().extensionArea
    extensionArea.registerExtensionPoint(ResolveOptimizingOptionsProvider.EP_NAME.name,
                                         ResolveOptimizingOptionsProvider::class.java.name,
                                         ExtensionPoint.Kind.INTERFACE,
                                         true)
    Disposer.register(disposable) {
        extensionArea.unregisterExtensionPoint(ResolveOptimizingOptionsProvider.EP_NAME.name)
    }
    return try {
        action()
    } finally {
        Disposer.dispose(disposable)
        services.forEach { (serviceInterface, _) ->
            project.unregisterService(serviceInterface)
        }
        services.forEach { (serviceInterface, _) ->
            check(project.getServiceIfCreated(serviceInterface) == null) {
                "Service ${serviceInterface} should be disposed"
            }
        }
    }

}

class DummyResolutionAnchorProvider : ResolutionAnchorProvider { override fun getResolutionAnchor(moduleDescriptor: ModuleDescriptor): ModuleDescriptor? = null }

internal data class ServiceWithImplementation<T : Any>(val serviceInterface: Class<T>, val createServiceInstance: (Project) -> T)
