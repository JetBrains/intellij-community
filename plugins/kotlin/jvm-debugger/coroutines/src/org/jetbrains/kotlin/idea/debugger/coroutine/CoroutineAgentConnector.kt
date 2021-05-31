// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.psi.JavaPsiFacade
import org.apache.maven.artifact.versioning.DefaultArtifactVersion

enum class CoroutineDebuggerMode {
    DISABLED,
    VERSION_UP_TO_1_3_5,
    VERSION_1_3_6_AND_UP,
    VERSION_1_3_8_AND_UP,
}

internal object CoroutineAgentConnector {
    private data class KotlinxCoroutinesSearchResult(val jarPath: String?, val debuggerMode: CoroutineDebuggerMode)

    private const val kotlinxCoroutinesCoreName = "kotlinx-coroutines-core"
    private const val kotlinStdlibName = "kotlin-stdlib"
    private const val kotlinxCoroutinesPackageName = "kotlinx.coroutines"
    private const val jarSeparator = "!/"
    private val versionToCompareTo = DefaultArtifactVersion("1.3.7-255")
    private val kotlinxCoroutinesCoreJarRegex = Regex(""".+\W$kotlinxCoroutinesCoreName(-jvm)?-(\d[\w.\-]+)?\.jar""")

    fun attachCoroutineAgent(project: Project, params: JavaParameters): Boolean {
        val searchResult = findKotlinxCoroutinesCoreJar(project)
        if (searchResult.debuggerMode == CoroutineDebuggerMode.VERSION_1_3_8_AND_UP &&
            searchResult.jarPath != null) {
            return initializeCoroutineAgent(params, searchResult.jarPath)
        }
        return false
    }

    private fun findKotlinxCoroutinesCoreJar(project: Project): KotlinxCoroutinesSearchResult {
        val matchResult = project.getJarPaths(kotlinxCoroutinesPackageName)
                .asSequence()
                .map { kotlinxCoroutinesCoreJarRegex.matchEntire(it) }
                .firstOrNull { it != null }

        if (matchResult == null || matchResult.groupValues.size < 3) {
            return KotlinxCoroutinesSearchResult(null, CoroutineDebuggerMode.DISABLED)
        }
        return KotlinxCoroutinesSearchResult(
            matchResult.value,
            determineCoreVersionMode(matchResult.groupValues[2])
        )
    }

    private fun Project.getJarPaths(packageName: String): List<String> {
        val kotlinxCoroutinesPackage = JavaPsiFacade.getInstance(this)
            .findPackage(packageName)
            ?: return emptyList()

        return kotlinxCoroutinesPackage.directories.mapNotNull {
            it.virtualFile.path.getParentJarPath()
        }
    }

    private fun String.getParentJarPath(): String? {
        val i = indexOf(jarSeparator)
        if (i != -1) {
            return substring(0, i)
        }
        return null
    }

    private fun determineCoreVersionMode(version: String) =
        if (DefaultArtifactVersion(version) > versionToCompareTo)
            CoroutineDebuggerMode.VERSION_1_3_8_AND_UP
        else
            CoroutineDebuggerMode.DISABLED

    private fun initializeCoroutineAgent(params: JavaParameters, jarPath: String): Boolean {
        val vmParametersList = params.vmParametersList ?: return false
        vmParametersList.add("-javaagent:$jarPath")
        // Fix for NoClassDefFoundError: kotlin/collections/AbstractMutableMap via CommandLineWrapper.
        // If classpathFile used in run configuration - kotlin-stdlib should be included in the -classpath
        if (params.isClasspathFile) {
            params.classPath.rootDirs.filter { it.path.contains(kotlinStdlibName) }.forEach {
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
