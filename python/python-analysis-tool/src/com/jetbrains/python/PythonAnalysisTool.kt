package com.jetbrains.python

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.SimpleCompletionProcess
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.core.CoreFileTypeRegistry
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.MockPsiApplication
import com.intellij.openapi.editor.impl.MockEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.impl.MockPsiProject
import com.intellij.openapi.util.Getter
import com.intellij.openapi.vfs.TestLocalVirtualFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.PlatformUtils
import kotlin.math.max

class PythonAnalysisTool {
  private val myParentDisposable = HeadDisposable()
  private val myFileTypeManager = CoreFileTypeRegistry()
  private val myApplication: MockPsiApplication = MockPsiApplication(myParentDisposable)
    .also { ApplicationManager.setApplication(it, Getter { myFileTypeManager }, myParentDisposable) }
  private val myProject: MockPsiProject = MockPsiProject(myApplication)
  private val myFileSystem = TestLocalVirtualFileSystem()
  private val myCompletionService: CompletionService

  init {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, "PythonAnalysisTool")
    val loadedPlugins = PluginManagerCore.getLoadedPlugins(PythonAnalysisTool::class.java.classLoader)
    myApplication.registerComponents(loadedPlugins)
    myApplication.picoContainer.registerComponentInstance(FileTypeRegistry::class.java, myFileTypeManager)
    myProject.registerComponents(loadedPlugins)
    myFileTypeManager.registerFileType(PythonFileType.INSTANCE, "py")
    myCompletionService = CompletionService.getCompletionService()
  }

  fun configureByText(text: String): PsiFile? {
    val file = myFileSystem.createTextVirtualFile("a.py", text)
    return PsiManager.getInstance(myProject).findFile(file)
  }

  fun resolve(file: PsiFile, offset: Int): PsiElement? = file.findElementAt(offset)

  fun completion(file: PsiFile, offset: Int): List<LookupElement> {
    val position = file.findElementAt(max(0, offset - 1))
    requireNotNull(position)

    val editor = MockEditor(file.virtualFile)
    editor.caretModel.moveToOffset(offset)
    val params = CompletionParameters(position, file, CompletionType.BASIC, max(0, offset - 1), 1, editor, SimpleCompletionProcess.INSTANCE)
    val lookupElements = mutableListOf<LookupElement>()
    myCompletionService.performCompletion(params) { result ->
      lookupElements.add(result.lookupElement)
    }
    return lookupElements
  }

  inner class HeadDisposable : Disposable {
    @Volatile
    private var myDisposed: Boolean = false

    override fun dispose() {
      myDisposed = true
    }

    override fun toString(): String = "HeadDisposable"
  }
}

fun main() {
  val analysisTool = PythonAnalysisTool()
  println("Enter text:")
  val sb = StringBuilder()
  while (true) {
    val text = readLine()
    if (text == "EOF") break
    sb.append(text + "\n")
  }

  val file = analysisTool.configureByText(sb.toString()) ?: return
  println("""
    Enter command:
    r[offset] - resolve
    c[offset] - complete
    x - exit
  """.trimIndent())
  while (true) {
    val command = readLine() ?: return
    if (command.startsWith("c")) {
      val offset = command.substring(1).toInt()
      val completions = analysisTool.completion(file, offset)
      if (completions.isEmpty()) {
        println("No completion found")
      } else {
        completions.forEach {
          println(it.lookupString)
        }
      }
    } else if (command.startsWith("r")) {
      val offset = command.substring(1).toInt()
      val resolve = analysisTool.resolve(file, offset)
      if (resolve != null) {
        println(resolve.toString())
      }
      else {
        println("Not resolved")
      }
    } else if (command.startsWith("x")) {
      return
    } else {
      println("Unknown command")
    }
  }
}