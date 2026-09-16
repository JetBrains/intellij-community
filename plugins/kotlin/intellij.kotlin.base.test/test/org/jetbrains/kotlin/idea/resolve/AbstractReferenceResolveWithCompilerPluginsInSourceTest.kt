// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.resolve

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Tests reference resolution for declarations that source compiler plugins generate.
 * Use `// COMPILER_PLUGIN_PRESET: <plugin>` in the main test file to select a plugin.
 */
abstract class AbstractReferenceResolveWithCompilerPluginsInSourceTest : AbstractReferenceResolveTest() {

    protected val testDirectoryPath: String
        get() = KotlinTestUtils.getTestDataFileName(this::class.java, this.name)!!

    override fun fileName(): String {
        return "$testDirectoryPath/${getTestName(true)}.kt"
    }

    override fun doTest(path: String) {
        val fileText = dataFile().readText()
        val compilerPlugin = parseCompilerPlugin(fileText)
        var libraryConfigured = false

        try {
            val libraryJar = project.loadSingleJarFromMaven(compilerPlugin.libraryCoordinates)
            ConfigLibraryUtil.addLibrary(module, compilerPlugin.libraryCoordinates) {
                addRoot(libraryJar, OrderRootType.CLASSES)
            }
            libraryConfigured = true

            val pluginArgument = "-Xplugin=${compilerPlugin.jarPath.absolutePathString()}"
            withCustomCompilerOptions(
                "$fileText\n// COMPILER_ARGUMENTS: $pluginArgument",
                project,
                module,
            ) {
                super.doTest(path)
            }
        }
        finally {
            if (libraryConfigured) {
                check(ConfigLibraryUtil.removeLibrary(module, compilerPlugin.libraryCoordinates))
            }
        }
    }

    private fun parseCompilerPlugin(fileText: String): CompilerPlugin {
        val pluginNames = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, COMPILER_PLUGIN_PRESET_DIRECTIVE)
        val pluginName = pluginNames.singleOrNull()
            ?: error("Specify exactly one $COMPILER_PLUGIN_PRESET_DIRECTIVE directive")
        return CompilerPlugin.entries.singleOrNull { it.name == pluginName }
            ?: error("Unknown compiler plugin: $pluginName. Available values: ${CompilerPlugin.entries.joinToString { it.name }}")
    }

    private enum class CompilerPlugin(
        private val registrarClassName: String,
        val libraryCoordinates: String,
    ) {
        LOMBOK(
            "org.jetbrains.kotlin.lombok.LombokComponentRegistrar",
            "org.projectlombok:lombok:1.18.26",
        ),

        SERIALIZATION(
            "org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar",
            KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES,
        );

        val jarPath: Path
            get() {
                val registrarClass = Class.forName(registrarClassName)
                return PathManager.getJarForClass(registrarClass) ?: error("Jar file for $registrarClass not found")
            }
    }

    companion object {
        private const val COMPILER_PLUGIN_PRESET_DIRECTIVE = "// COMPILER_PLUGIN_PRESET:"
    }
}
