// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST")

package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.unregisterService
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.KotlinCacheServiceImpl
import org.jetbrains.kotlin.idea.compiler.IdeModuleAnnotationsResolver
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.resolve.CodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.DummyCodeAnalyzerInitializer
import org.jetbrains.kotlin.resolve.ModuleAnnotationsResolver
import org.jetbrains.kotlin.resolve.ResolutionAnchorProvider

/**
 * Needed for DebuggerTestCompilerFacility to be able to compile testdata, probably need to be rewritten to compile with K2 compiler
 */
internal inline fun <R> withTestServicesNeededForCodeCompilation(project: Project, action: () -> R): R {
    val disposable = Disposer.newCheckedDisposable("withTestServicesNeededForCodeCompilation")
    val services = listOf(
        ServiceWithImplementation(ScriptDependenciesModificationTracker::class.java) { ScriptDependenciesModificationTracker() },
        ServiceWithImplementation(KotlinCacheService::class.java, ::KotlinCacheServiceImpl),
        ServiceWithImplementation(ResolutionAnchorProvider::class.java) { DummyResolutionAnchorProvider() },
        ServiceWithImplementation(CodeAnalyzerInitializer::class.java) { DummyCodeAnalyzerInitializer() },
        ServiceWithImplementation(ModuleAnnotationsResolver::class.java, ::IdeModuleAnnotationsResolver),
    )

    services.forEach { (serviceInterface, createServiceInstance) ->
        val serviceInstance = createServiceInstance(project)
        (serviceInstance as? Disposable)?.let { Disposer.register(disposable, it) }
        project.registerServiceInstance(serviceInterface as Class<Any>, serviceInstance)
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