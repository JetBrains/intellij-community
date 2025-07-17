// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import kotlin.sequences.mapNotNull

internal object CoroutineAgentConnector {
    private data class KotlinxCoroutinesSearchResult(val jarPath: String?, val debuggerMode: CoroutineDebuggerMode)

    private const val KOTLIN_STDLIB = "kotlin-stdlib"
    private const val KOTLINX_COROUTINES_DEBUG_INTERNAL_PACKAGE = "kotlinx.coroutines.debug.internal"
    private val MINIMAL_SUPPORTED_COROUTINES_VERSION = DefaultArtifactVersion("1.3.7-255")
    private const val KOTLINX_COROUTINES_CORE = "kotlinx-coroutines-core"
    private val KOTLINX_COROUTINES_CORE_JVM_JAR_REGEX = Regex(""".+\W$KOTLINX_COROUTINES_CORE(-jvm)?-(\d[\w.\-]+)?\.jar""")

    fun attachCoroutineAgent(project: Project, configuration: RunConfigurationBase<*>?,  params: JavaParameters): Boolean {
        val searchResult = findKotlinxCoroutinesCoreJar(project, configuration)
        if (searchResult.debuggerMode == CoroutineDebuggerMode.VERSION_1_3_8_AND_UP &&
            searchResult.jarPath != null) {
            return initializeCoroutineAgent(params, searchResult.jarPath)
        }
        return false
    }

    private fun findKotlinxCoroutinesCoreJar(project: Project, configuration: RunConfigurationBase<*>?): KotlinxCoroutinesSearchResult {
        val jarPaths = getKotlinxCoroutinesJarsFromClasspath(project, configuration)

        val newestKotlinxCoroutinesJar = jarPaths
            .asSequence()
            .mapNotNull { KOTLINX_COROUTINES_CORE_JVM_JAR_REGEX.matchEntire(it) }
            .maxByOrNull {
                DefaultArtifactVersion(it.groupValues[2])
            }

        if (newestKotlinxCoroutinesJar == null || newestKotlinxCoroutinesJar.groupValues.size < 3) {
            return KotlinxCoroutinesSearchResult(null, CoroutineDebuggerMode.DISABLED)
        }
        return KotlinxCoroutinesSearchResult(
            newestKotlinxCoroutinesJar.value,
            determineCoreVersionMode(newestKotlinxCoroutinesJar.groupValues[2])
        )
    }

    private fun getKotlinxCoroutinesJarsFromClasspath(project: Project, configuration: RunConfigurationBase<*>?): List<String> {
        // First, check if any modules corresponding to the given run configuration have loaded the kotlinx.coroutines.debug.internal package,
        // if none, then the coroutines debug agent should not be applied.
        // This is important for a case when a project contains both Kotlin and Java modules, Kotlin modules may contain coroutines dependency,
        // though when a Java run configuration is chosen, the coroutine agent should not be applied.
        // In case no run configuration is provided or if it's not module-based, then search the whole project for the package.
        val coroutinesPsiPackage = JavaPsiFacade.getInstance(project).findPackage(KOTLINX_COROUTINES_DEBUG_INTERNAL_PACKAGE) ?: return emptyList()

        return if (configuration != null && configuration is ModuleBasedConfiguration<*, *>) {
            configuration.modules.flatMap { module ->
                val moduleScope = GlobalSearchScope.union(
                    listOf(
                        GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module),
                        module.getModuleRuntimeScope(true),
                    )
                )
                coroutinesPsiPackage.getDirectories(moduleScope).mapNotNull {
                    JarFileSystem.getInstance().getVirtualFileForJar(it.virtualFile)?.path
                }
            }
        } else {
            coroutinesPsiPackage.getDirectories()
                .mapNotNull {
                    JarFileSystem.getInstance().getVirtualFileForJar(it.virtualFile)?.path
                }
        }
    }

    private fun determineCoreVersionMode(version: String) =
        if (DefaultArtifactVersion(version) > MINIMAL_SUPPORTED_COROUTINES_VERSION)
            CoroutineDebuggerMode.VERSION_1_3_8_AND_UP
        else
            CoroutineDebuggerMode.DISABLED

    private fun initializeCoroutineAgent(params: JavaParameters, jarPath: String): Boolean {
        val vmParametersList = params.vmParametersList ?: return false
        vmParametersList.add("-javaagent:$jarPath")
        // Fix for NoClassDefFoundError: kotlin/collections/AbstractMutableMap via CommandLineWrapper.
        // If classpathFile used in run configuration - kotlin-stdlib should be included in the -classpath
        if (params.isClasspathFile) {
            params.classPath.rootDirs.filter { it.path.contains(KOTLIN_STDLIB) }.forEach {
                val path = when (val fs = it.fileSystem) {
                    is ArchiveFileSystem -> fs.getLocalByEntry(it)?.path
                    else -> it.path
                }
                it.putUserData(JdkUtil.AGENT_RUNTIME_CLASSPATH, path)
            }
        }

        return true
    }
}

private enum class CoroutineDebuggerMode {
    DISABLED,
    VERSION_1_3_8_AND_UP,
}
