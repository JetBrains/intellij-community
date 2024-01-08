// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.jarFile
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import java.io.File
import kotlin.test.assertNotNull

const val CONFIGURE_LIBRARY_PREFIX = "CONFIGURE_LIBRARY:"
const val LIBRARY_JAR_FILE_PREFIX = "LIBRARY_JAR_FILE:"

/**
 * Helper for configuring kotlin runtime in tested project.
 */
object ConfigLibraryUtil {
    private const val LIB_NAME_JAVA_RUNTIME = "KOTLIN_STDLIB_LIB_NAME"
    private const val LIB_NAME_KOTLIN_TEST = "KOTLIN_TEST_LIB_NAME"
    private const val LIB_NAME_KOTLIN_STDLIB_JS = "KOTLIN_STDLIB_JS_LIB_NAME"
    private const val LIB_NAME_KOTLIN_STDLIB_COMMON = "KOTLIN_STDLIB_COMMON_LIB_NAME"

    val ATTACHABLE_LIBRARIES = mapOf(
      "KotlinTestJunit" to TestKotlinArtifacts.kotlinTestJunit,
      "JUnit" to File(PathUtil.getJarPathForClass(junit.framework.TestCase::class.java)),
      "JUnit3" to TestKotlinArtifacts.junit3,
      "JUnit4" to File(PathUtil.getJarPathForClass(junit.framework.TestCase::class.java)),
      "JUnit5" to File(PathUtil.getJarPathForClass(org.junit.jupiter.api.Test::class.java)),
      "TestNG" to File(PathUtil.getJarPathForClass(org.testng.annotations.Test::class.java)),
      "Coroutines" to File(PathUtil.getJarPathForClass(kotlinx.coroutines.Job::class.java)),
    )

    fun configureKotlinRuntimeAndSdk(module: Module, sdk: Sdk) {
        configureSdk(module, sdk)
        configureKotlinRuntime(module)
    }

    fun configureKotlinStdlibJs(module: Module) {
        addLibrary(module, LIB_NAME_KOTLIN_STDLIB_JS, KotlinJavaScriptLibraryKind) {
            addRoot(TestKotlinArtifacts.kotlinStdlibJs, OrderRootType.CLASSES)
        }
    }

    fun configureKotlinStdlibCommon(module: Module) {
        addLibrary(module, LIB_NAME_KOTLIN_STDLIB_COMMON, KotlinCommonLibraryKind) {
            addRoot(TestKotlinArtifacts.kotlinStdlibCommon, OrderRootType.CLASSES)
        }
    }

    fun configureKotlinRuntime(module: Module) {
        addLibrary(module, LIB_NAME_JAVA_RUNTIME) {
            addRoot(TestKotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
        }

        addLibrary(module, LIB_NAME_KOTLIN_TEST) {
            addRoot(TestKotlinArtifacts.kotlinTest, OrderRootType.CLASSES)
        }
    }

    fun unconfigureKotlinRuntime(module: Module) {
        removeLibrary(module, LIB_NAME_JAVA_RUNTIME)
        removeLibrary(module, LIB_NAME_KOTLIN_TEST)
    }

    fun unConfigureKotlinRuntimeAndSdk(module: Module, sdk: Sdk) {
        configureSdk(module, sdk)
        unconfigureKotlinRuntime(module)
    }

    fun unConfigureKotlinJsRuntimeAndSdk(module: Module, sdk: Sdk) {
        configureSdk(module, sdk)
        removeLibrary(module, LIB_NAME_KOTLIN_STDLIB_JS)
    }

    fun unConfigureKotlinCommonRuntime(module: Module) {
        removeLibrary(module, LIB_NAME_KOTLIN_STDLIB_COMMON)
    }

    fun configureSdk(module: Module, sdk: Sdk) {
        ApplicationManager.getApplication().runWriteAction {
            val rootManager = ModuleRootManager.getInstance(module)
            val rootModel = rootManager.modifiableModel

            assertNotNull(
                ProjectJdkTable.getInstance().findJdk(sdk.name),
                "Cannot find sdk in ProjectJdkTable. This may cause sdk leak.\n" +
                        "You can use ProjectPluginTestBase.addJdk(Disposable ...) to register sdk in ProjectJdkTable.\n" +
                        "Then sdk will be removed in tearDown"
            )

            rootModel.sdk = sdk
            rootModel.commit()
        }
    }

    fun addProjectLibrary(
        project: Project,
        name: String,
        init: Library.ModifiableModel.() -> Unit,
    ): Library = runWriteAction {
        LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary(name).apply {
            modifiableModel.init()
        }
    }

    fun addProjectLibraryWithClassesRoot(project: Project, name: String): Library = runWriteAction {
        addProjectLibrary(project, name) {
            // Add a unique root to avoid deduplication of library infos in tests (see `LibraryInfoCache`).
            addEmptyClassesRoot()
            commit()
        }
    }

    fun removeProjectLibrary(project: Project, library: Library) {
        runWriteAction {
            LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeLibrary(library)
        }
    }

    fun addLibrary(module: Module, name: String, kind: PersistentLibraryKind<*>? = null, init: Library.ModifiableModel.() -> Unit) {
        runWriteAction {
            ModuleRootManager.getInstance(module).modifiableModel.apply {
                addLibrary(this, name, kind, init)
                commit()
            }
        }
    }

    fun addLibrary(
        rootModel: ModifiableRootModel,
        name: String, kind: PersistentLibraryKind<*>? = null,
        init: Library.ModifiableModel.() -> Unit
    ) {
        rootModel.moduleLibraryTable.modifiableModel.apply {
            val library = createLibrary(name, kind)
            library.modifiableModel.apply {
                init()
                commit()
            }

            commit()
        }
    }

    fun addLibrary(editor: NewLibraryEditor, model: ModifiableRootModel, kind: PersistentLibraryKind<*>? = null): Library {
        val libraryTableModifiableModel = model.moduleLibraryTable.modifiableModel
        val library = libraryTableModifiableModel.createLibrary(editor.name, kind)

        val libModel = library.modifiableModel
        try {
            editor.applyTo(libModel as LibraryEx.ModifiableModelEx)
        } finally {
            libModel.commit()
            libraryTableModifiableModel.commit()
        }

        return library
    }

    fun removeLibrary(module: Module, libraryName: String): Boolean {
        return runWriteAction {
            var removed = false

            val rootManager = ModuleRootManager.getInstance(module)
            val model = rootManager.modifiableModel

            for (orderEntry in model.orderEntries) {
                if (orderEntry is LibraryOrderEntry) {

                    val library = orderEntry.library
                    if (library != null) {
                        val name = library.name
                        if (name != null && name == libraryName) {

                            // Dispose attached roots
                            val modifiableModel = library.modifiableModel
                            for (rootUrl in library.rootProvider.getUrls(OrderRootType.CLASSES)) {
                                modifiableModel.removeRoot(rootUrl, OrderRootType.CLASSES)
                            }
                            for (rootUrl in library.rootProvider.getUrls(OrderRootType.SOURCES)) {
                                modifiableModel.removeRoot(rootUrl, OrderRootType.SOURCES)
                            }
                            modifiableModel.commit()

                            model.removeOrderEntry(orderEntry)

                            removed = true
                            break
                        }
                    }
                }
            }

            model.commit()

            removed
        }
    }

    private fun configureLibraries(module: Module, libraryNames: List<String>) {
        for (libraryName in libraryNames) {
            addLibrary(module, libraryName) {
                val jar = ATTACHABLE_LIBRARIES[libraryName] ?: error("$libraryName isn't registered")
                addRoot(jar, OrderRootType.CLASSES)
            }
        }
    }

    private fun unconfigureLibrariesByName(module: Module, libraryNames: MutableList<String>) {
        val iterator = libraryNames.iterator()
        while (iterator.hasNext()) {
            val libraryName = iterator.next()
            if (removeLibrary(module, libraryName)) {
                iterator.remove()
            }
        }

        if (libraryNames.isNotEmpty()) throw AssertionError("Couldn't find the following libraries: $libraryNames")
    }

    fun configureLibrariesByDirective(module: Module, testDataDirectory: File, fileText: String) {
        configureLibrariesByDirective(module, fileText)
        val customLibraries = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// $LIBRARY_JAR_FILE_PREFIX ")
        if (customLibraries.isNotEmpty()) {
            for (customLibrary in customLibraries) {
                addLibrary(module, customLibrary) {
                    val jar = File(testDataDirectory, customLibrary).takeIf(File::exists) ?: error("$customLibrary doesn't exist")
                    addRoot(jar, OrderRootType.CLASSES)
                }
            }
        }
    }

    fun configureLibrariesByDirective(module: Module, fileText: String) {
        configureLibraries(module, InTextDirectivesUtils.findListWithPrefixes(fileText, CONFIGURE_LIBRARY_PREFIX))
    }

    fun unconfigureLibrariesByDirective(module: Module, fileText: String) {
        val libraryNames =
            InTextDirectivesUtils.findListWithPrefixes(fileText, CONFIGURE_LIBRARY_PREFIX) +
            InTextDirectivesUtils.findListWithPrefixes(fileText, "// UNCONFIGURE_LIBRARY: ") +
            InTextDirectivesUtils.findListWithPrefixes(fileText, "// $LIBRARY_JAR_FILE_PREFIX ")

        unconfigureLibrariesByName(module, libraryNames.toMutableList())
    }
}

fun Library.ModifiableModel.addRoot(file: File, kind: OrderRootType) {
    addRoot(VfsUtil.getUrlForLibraryRoot(file), kind)
}

fun Library.ModifiableModel.addEmptyClassesRoot() {
    val jarFile = jarFile { }.generateInTempDir()
    addRoot(jarFile.toFile(), OrderRootType.CLASSES)
}
