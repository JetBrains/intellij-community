package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import org.apache.maven.artifact.versioning.DefaultArtifactVersion

enum class CoroutineDebuggerMode {
    DISABLED,
    VERSION_UP_TO_1_3_5,
    VERSION_1_3_6_AND_UP,
    VERSION_1_3_8_AND_UP,
}

object CoroutineAgentConnector {
    private data class KotlinxCoroutinesSearchResult(val jarPath: String?, val debuggerMode: CoroutineDebuggerMode)

    private const val kotlinxCoroutinesCoreName = "kotlinx-coroutines-core"
    private val versionToCompareTo = DefaultArtifactVersion("1.3.7-255")
    private val kotlinxCoroutinesCoreJarRegex = Regex(""".+\W$kotlinxCoroutinesCoreName(-jvm)?-(\d[\w.\-]+)?\.jar""")

    fun attachCoroutineAgent(params: JavaParameters): Boolean {
        val searchResult = findKotlinxCoroutinesCoreJar(params.classPath?.pathList)
        if (searchResult.debuggerMode == CoroutineDebuggerMode.VERSION_1_3_8_AND_UP &&
            searchResult.jarPath != null) {
            return initializeCoroutineAgent(params, searchResult.jarPath)
        }
        return false
    }

    private fun findKotlinxCoroutinesCoreJar(pathList: List<String>?): KotlinxCoroutinesSearchResult {
        fun emptyResult() = KotlinxCoroutinesSearchResult(null, CoroutineDebuggerMode.DISABLED)

        val kotlinxCoroutinesCoreCandidates = pathList?.filter {
            it.contains(kotlinxCoroutinesCoreName)
        } ?: return emptyResult()

        val matchResult = kotlinxCoroutinesCoreCandidates.asSequence()
            .map { kotlinxCoroutinesCoreJarRegex.matchEntire(it) }
            .firstOrNull { it != null }

        if (matchResult == null || matchResult.groupValues.size < 3) {
            return emptyResult()
        }

        return KotlinxCoroutinesSearchResult(
            matchResult.value,
            determineCoreVersionMode(matchResult.groupValues[2])
        )
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
            params.classPath.rootDirs.filter { it.isKotlinStdlib() }.forEach {
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
