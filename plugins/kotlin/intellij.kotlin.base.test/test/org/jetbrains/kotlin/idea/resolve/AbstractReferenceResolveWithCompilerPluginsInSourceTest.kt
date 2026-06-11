// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Variant of [AbstractReferenceResolveWithCompilerPluginsWithLibTest] which keeps
 * compiler-plugin-annotated declarations in project sources (not in a compiled library),
 * so that references between source files are exercised with the plugin enabled.
 *
 * Each test directory is laid out as a flat folder. The main file (`<testName>.kt`)
 * holds carets and `// REF:` directives; sibling files matching the `.Data.kt` suffix
 * are configured into the same fixture via
 * [org.jetbrains.kotlin.idea.completion.test.configureByFilesWithSuffixes].
 *
 * The kotlinx.serialization runtime is added as a module library to make
 * `@Serializable` and friends visible, and the bundled kotlinx.serialization
 * compiler plugin is enabled via the standard
 * `// COMPILER_ARGUMENTS: -Xplugin=...` mechanism.
 */
abstract class AbstractReferenceResolveWithCompilerPluginsInSourceTest : AbstractReferenceResolveTest() {

    protected val testDirectoryPath: String
        get() = KotlinTestUtils.getTestDataFileName(this::class.java, this.name)!!

    override fun fileName(): String {
        return "$testDirectoryPath/${getTestName(true)}.kt"
    }

    override fun setUp() {
        super.setUp()

        val serializationCoreJar = project.loadSingleJarFromMaven(KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES)
        ConfigLibraryUtil.addLibrary(module, SERIALIZATION_LIB_NAME) {
            addRoot(serializationCoreJar, OrderRootType.CLASSES)
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { ConfigLibraryUtil.removeLibrary(module, SERIALIZATION_LIB_NAME) },
            ThrowableRunnable { super.tearDown() },
        )
    }

    override fun doTest(path: String) {
        val pluginArgument = "-Xplugin=${KOTLINX_SERIALIZATION_COMPILER_PLUGIN_PATH.absolutePathString()}"
        withCustomCompilerOptions(
            "// COMPILER_ARGUMENTS: $pluginArgument",
            project,
            module,
        ) {
            super.doTest(path)
        }
    }

    companion object {
        private const val SERIALIZATION_LIB_NAME = "kotlinx-serialization-core"

        /**
         * Dynamically resolves the location of the `SerializationComponentRegistrar` class
         * to determine the associated plugin jar path.
         *
         * Mirrors [AbstractReferenceResolveWithCompilerPluginsWithLibTest]; kept here to
         * avoid depending on the bundled-compiler-plugins module from this base.
         */
        private val KOTLINX_SERIALIZATION_COMPILER_PLUGIN_PATH: Path
            get() {
                val registrarClass = Class.forName("org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar")
                return PathManager.getJarForClass(registrarClass) ?: error("Jar file for $registrarClass not found")
            }
    }
}
