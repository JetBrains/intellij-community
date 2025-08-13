package com.intellij.python.sdk.ui.evolution.tool.poetry.sdk

//import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdkProvider
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.resolvePythonHome
import java.nio.file.Path
import kotlin.io.path.name

internal object PoetryEvoSdkManager  {
  fun buildEvoSdk(module: Module, pythonBinaryPath: Path): EvoSdk {
    val sdkHome = pythonBinaryPath.resolvePythonHome()
    val venvName = sdkHome.name
    val name = venvName.takeIf { !it.startsWith(module.name.lowercase()) }

    return EvoSdk(
      icon = PythonSdkUIIcons.Tools.Poetry,
      name = name,
      pythonBinaryPath = pythonBinaryPath,
    )
  }
}


internal object PoetryEvoSdkProvider : EvoSdkProvider {
  override fun parsePySdk(module: Module, sdk: Sdk): EvoSdk? {
    //if (sdk.sdkAdditionalData !is PyPoetrySdkAdditionalData) return null
    val sdkHomePath = sdk.homePath?.let { Path.of(it) } ?: return null
    val evoSdk = PoetryEvoSdkManager.buildEvoSdk(module, sdkHomePath)
    return evoSdk
  }
}



