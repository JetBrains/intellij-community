package com.jetbrains.python.sdk

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.system.OS
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import java.nio.file.Path
import kotlin.io.path.Path

val Editor.pythonSdk: Sdk?
  get() {
    val project = project ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val viewProvider = psiFile?.viewProvider ?: return null
    val pyPsiFile = viewProvider.allFiles.firstOrNull { it is PyFile } ?: return null
    return PythonSdkUtil.findPythonSdk(pyPsiFile)
  }

fun Sdk.getExecutablePath(name: String): Path? = homePath?.let {
  val base = Path(it)
  if (OS.CURRENT == OS.Windows) {
    val candidates = listOf("exe", "bat", "cmd", "com").map { ext -> Path(it + "." + ext) }
    val found = candidates.firstNotNullOfOrNull { candidate -> PythonSdkUtil.getExecutablePath(candidate, name) }
    found ?: PythonSdkUtil.getExecutablePath(base, name)
  }
  else {
    PythonSdkUtil.getExecutablePath(base, name)
  }
}