package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.getUpdateEnvVarsWithPrependedPath
import com.intellij.ide.starter.utils.updatePathEnvVariable
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import com.intellij.util.text.SemVer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

const val TSGO_VERSION: String = "v0.0.5"

fun downloadAndConfigureNodejs(version: String): Path {
  val arch = when {
    SystemInfo.isMac && CpuArch.isIntel64() -> "darwin-x64"
    SystemInfo.isMac && CpuArch.isArm64() -> {
      if (SemVer.parseFromText(version)?.isGreaterOrEqualThan(16, 0, 0) == true)
        "darwin-arm64"
      else
        error(
          "Platform ${SystemInfo.OS_NAME} @ ${CpuArch.CURRENT} is supported in version 16.0.0 or higher. Requested version: $version")
    }
    SystemInfo.isLinux && CpuArch.isIntel64() -> "linux-x64"
    SystemInfo.isLinux && CpuArch.isArm64() -> "linux-arm64"
    SystemInfo.isWindows && CpuArch.isIntel64() -> "win-x64"
    else -> error("Platform ${SystemInfo.OS_NAME} @ ${CpuArch.CURRENT} is not supported")
  }
  val extension = if (SystemInfo.isWindows) ".zip" else ".tar.gz"
  val fileNameWithoutExt = "node-v$version-$arch"
  val url = "https://nodejs.org/dist/v$version/$fileNameWithoutExt$extension"
  val dirToDownload = GlobalPaths.instance.getCacheDirectoryFor("nodejs")
  val downloadedFile = dirToDownload.resolve("$fileNameWithoutExt$extension")
  val nodejsRoot = dirToDownload.resolve(fileNameWithoutExt)
  val nodePath = buildNodePath(nodejsRoot)

  if (nodejsRoot.exists()) {
    return nodePath
  }

  HttpClient.download(url, downloadedFile)
  FileSystem.unpack(downloadedFile, dirToDownload)
  enableCorepack(nodePath)
  return nodePath
}

fun IDETestContext.setUseTypesFromServer(value: Boolean): IDETestContext = applyVMOptionsPatch {
  addSystemProperty("typescript.compiler.evaluation", value.toString())
}

fun IDETestContext.setAbortTypeScriptCompilerRequestsOutsideProject(value: Boolean): IDETestContext = applyVMOptionsPatch {
  addSystemProperty("typescript.service.abort.requests.outside.project", value.toString())
}

fun IDETestContext.setTypeScriptServiceNodeArguments(value: String): IDETestContext = applyVMOptionsPatch {
  addSystemProperty("typescript.service.node.arguments", value)
}

fun IDETestContext.updatePath(path: Path): IDETestContext = applyVMOptionsPatch {
  updatePathEnvVariable(path)
}

fun IDETestContext.setTSGOtypeEvaluator(value: Boolean): IDETestContext =
  if (!value) this else applyVMOptionsPatch {
    addSystemProperty("typescript.ts-go.type-evaluator.path", getTsGoExecutablePath())
  }

fun writeConfigWithEmbeddedTSGo(context: IDETestContext) {
  val compilerXMLFilePath = context.resolvedProjectHome.resolve(".idea").resolve("compiler.xml")
  compilerXMLFilePath.parent.createDirectories()
  compilerXMLFilePath.writeText("""
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="TypeScriptCompiler">
        <option name="versionType" value="EMBEDDED_TS_GO" />
      </component>
    </project>
  """.trimIndent())
}

private fun getTsGoExecutablePath(): Path {
  val tsGoBinOS = when (OS.CURRENT) {
    OS.macOS if CpuArch.isArm64() -> "darwin-arm64"
    OS.Linux if CpuArch.isIntel64() -> "linux-amd64"
    else -> {
      error(
        "unsupported OS: ${OS.CURRENT}")
    }
  }

  val tsGoBinName = "tsgo-$TSGO_VERSION-$tsGoBinOS"
  val tsGoBinPath = GlobalPaths.instance.getCacheDirectoryFor("tsgo/bin").resolve(tsGoBinName)

  if (tsGoBinPath.exists()) {
    return tsGoBinPath
  }

  HttpClient.download("https://packages.jetbrains.team/files/p/ij/intellij-test-data-public/webstorm/tsgo/bin/$tsGoBinName", tsGoBinPath)

  val process = ProcessBuilder("chmod", "a+x", tsGoBinPath.toString())
    .redirectErrorStream(true)
    .start()

  val exitCode = process.waitFor()
  if (exitCode == 0) {
    println("chmode has been successfully executed for $tsGoBinPath")
  } else {
    error("error in executing chmode for $tsGoBinPath")
  }

  return tsGoBinPath
}

private fun buildNodePath(path: Path): Path {
  return if (SystemInfo.isWindows) path else path.resolve("bin")
}

private fun getApplicationExecutablePath(nodejsRoot: Path, application: String): Path {
  return if (SystemInfo.isWindows) nodejsRoot.resolve("$application.cmd") else nodejsRoot.resolve(application)
}

private fun enableCorepack(nodejsRoot: Path) {
  val corePackPath = getApplicationExecutablePath(nodejsRoot, "corepack")

  ProcessExecutor(presentableName = "corepack enable",
                  nodejsRoot,
                  timeout = 1.minutes,
                  args = listOf("$corePackPath", "--install-directory", ".", "enable"),
                  environmentVariables = getUpdateEnvVarsWithPrependedPath(nodejsRoot)
  ).start()
}

private fun getNodePathByVersion(version: String): Path {
  val nodeJSDir = GlobalPaths.instance.getCacheDirectoryFor("nodejs")

  val matchingFolder = Files.list(nodeJSDir)
    .filter { Files.isDirectory(it) }
    .filter { it.fileName.toString().contains("node-v$version") }
    .toList()
    .first()

  return buildNodePath(matchingFolder)
}
