package com.jetbrains.python

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.pyi.PyiFileType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PyLanguageFacade {
  abstract fun getEffectiveLanguageLevel(psiElement: PsiElement): LanguageLevel
  abstract fun getEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel
  abstract fun setEffectiveLanguageLevel(virtualFile: VirtualFile, languageLevel: LanguageLevel?)

  companion object {
    /**
     * The facade of the stub-based Python PSI, or the default facade of `intellij.python.parser` when no stub-based Python
     * PSI is loaded. The JetBrains Client frontend and JetBrains Light before the smart mode are such processes.
     */
    @JvmStatic
    val INSTANCE: PyLanguageFacade
      get() = ApplicationManager.getApplication().service<PyLanguageFacade>()

    /**
     * The file whose language level applies to [virtualFile]: the host file of an injected fragment, and the origin
     * file of a notebook or another backed file.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun originVirtualFile(virtualFile: VirtualFile): VirtualFile {
      val file = if (virtualFile is VirtualFileWindow) virtualFile.delegate else virtualFile
      return BackedVirtualFile.getOriginFileIfBacked(file)
    }
  }
}

private val LANGUAGE_LEVEL_KEY: Key<LanguageLevel> = Key.create("LANGUAGE_LEVEL_KEY")

/**
 * Keeps the language level of a file in its user data. A frontend receives the level of each file from its backend through
 * [setEffectiveLanguageLevel]. A file without a stored level gets [LanguageLevel.getDefault], and a `.pyi` stub file gets
 * [LanguageLevel.getLatest].
 */
internal class PyDefaultLanguageFacade : PyLanguageFacade() {
  override fun getEffectiveLanguageLevel(psiElement: PsiElement): LanguageLevel {
    if (psiElement is PsiDirectory) {
      return getEffectiveLanguageLevel(psiElement.project, psiElement.virtualFile)
    }
    val containingFile = psiElement.containingFile ?: return LanguageLevel.getDefault()
    return levelOfFile(containingFile)
  }

  override fun getEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel {
    val file = originVirtualFile(virtualFile)
    file.getUserData(LANGUAGE_LEVEL_KEY)?.let { return it }
    return if (file.fileType == PyiFileType.INSTANCE) LanguageLevel.getLatest() else LanguageLevel.getDefault()
  }

  override fun setEffectiveLanguageLevel(virtualFile: VirtualFile, languageLevel: LanguageLevel?) {
    virtualFile.putUserData(LANGUAGE_LEVEL_KEY, languageLevel)
  }
}

@ApiStatus.Internal
fun getEffectiveLanguageLevel(file: PsiFile): LanguageLevel = PyLanguageFacade.INSTANCE.levelOfFile(file)

/** The level of the original file of [file], by its virtual file. */
private fun PyLanguageFacade.levelOfFile(file: PsiFile): LanguageLevel {
  var curFile = file
  while (curFile != curFile.getOriginalFile()) {
    curFile = curFile.getOriginalFile()
  }
  val virtualFile = curFile.getVirtualFile() ?: curFile.getViewProvider().getVirtualFile()
  return getEffectiveLanguageLevel(curFile.project, virtualFile)
}
