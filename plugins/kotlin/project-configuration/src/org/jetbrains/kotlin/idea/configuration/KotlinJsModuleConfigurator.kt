// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryType
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.StdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.library.JSLibraryType
import org.jetbrains.kotlin.idea.projectConfiguration.JSLibraryStdDescription
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms

open class KotlinJsModuleConfigurator : KotlinWithLibraryConfigurator<DummyLibraryProperties>() {
    override val name: String
        get() = NAME

    override val targetPlatform: TargetPlatform
        get() = JsPlatforms.defaultJsPlatform

    override val presentableText: String
        get() = KotlinProjectConfigurationBundle.message("language.name.javascript")

    override fun isConfigured(module: Module): Boolean = hasKotlinJsLegacyRuntimeInScope(module)

    override val libraryName: String
        get() = JSLibraryStdDescription.LIBRARY_NAME

    override val dialogTitle: String
        get() = JSLibraryStdDescription.DIALOG_TITLE

    override val messageForOverrideDialog: String
        get() = JSLibraryStdDescription.JAVA_SCRIPT_LIBRARY_CREATION

    override val libraryJarDescriptor: LibraryJarDescriptor get() = LibraryJarDescriptor.JS_STDLIB_JAR

    override val libraryType: LibraryType<DummyLibraryProperties> get() = JSLibraryType.getInstance()

    override val libraryProperties: DummyLibraryProperties
        get() = DummyLibraryProperties.INSTANCE

    override val stdlibDetector: StdlibDetectorFacility
        get() = KotlinJavaScriptStdlibDetectorFacility

    companion object {
        const val NAME: String = "js"
    }

    /**
     * Migrate pre-1.1.3 projects which didn't have explicitly specified kind for JS libraries.
     */
    override fun findAndFixBrokenKotlinLibrary(module: Module, collector: NotificationMessageCollector): Library? {
        val allLibraries = mutableListOf<LibraryEx>()
        var brokenStdlib: Library? = null
        for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
            val library = (orderEntry as? LibraryOrderEntry)?.library as? LibraryEx ?: continue
            allLibraries.add(library)
            if (KotlinJavaScriptStdlibDetectorFacility.isStdlib(module.project, library, ignoreKind = true) && library.kind == null) {
                brokenStdlib = library
            }
        }

        if (brokenStdlib != null) {
            runWriteAction {
                for (library in allLibraries.filter { it.kind == null }) {
                    library.modifiableModel.apply {
                        kind = KotlinJavaScriptLibraryKind
                        commit()
                    }
                }
            }
            collector.addMessage(KotlinProjectConfigurationBundle.message("updated.javascript.libraries.in.module.0", module.name))
        }
        return brokenStdlib
    }
}
