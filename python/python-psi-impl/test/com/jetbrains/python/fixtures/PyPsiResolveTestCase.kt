package com.jetbrains.python.fixtures

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PyBuiltinCache
import org.intellij.lang.annotations.Language

abstract class PyPsiResolveTestCase : PyPsiTestCase() {

  abstract fun doResolve(): PsiElement?

  protected fun <T : PsiElement> assertResolvesTo(langLevel: LanguageLevel, aClass: Class<T>, name: String): T {
    val result = Ref<T>()

    runWithLanguageLevel(
      langLevel
    ) { result.set(assertResolvesTo(aClass, name, null)) }

    return result.get()
  }

  protected fun <T : PsiElement> assertResolvesTo(aClass: Class<T>, name: String): T {
    return assertResolvesTo(aClass, name, null)
  }

  protected fun <T : PsiElement> assertResolvesTo(aClass: Class<T>, name: String, containingFilePath: String?): T {
    val element =
      try {
        doResolve()
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }

    return assertResolveResult(element, aClass, name, containingFilePath)
  }

  protected fun assertUnresolved() {
    val element =
      try {
        doResolve()
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }

    assertNull(element)
  }

  protected fun <T : PsiNamedElement> assertResolvesTo(@Language("TEXT") text: String, cls: Class<T>, name: String): T {
    val result = Ref<T>()

    runWithLanguageLevel(
      LanguageLevel.getLatest()
    ) {
      configureByText(PythonFileType.INSTANCE, text)
      val element = myPsiFile?.let { findReferenceByMarker(it) }?.resolve()
      result.set(assertResolveResult(element, cls, name))
    }

    return result.get()
  }

  companion object {
    fun <T : PsiElement> assertResolveResult(element: PsiElement?, aClass: Class<T>, name: String): T {
      return assertResolveResult(element, aClass, name, null)
    }

    fun <T : PsiElement> assertResolveResult(element: PsiElement?, aClass: Class<T>, name: String, containingFilePath: String?): T {
      assertInstanceOf(element, aClass)
      assertEquals(name, (element as PsiNamedElement).name)
      if (containingFilePath != null) {
        val virtualFile = element.getContainingFile().virtualFile
        assertEquals(containingFilePath, virtualFile.path)
      }
      return element as T
    }

    fun assertIsBuiltin(element: PsiElement?) {
      assertNotNull(element)
      assertTrue(PyBuiltinCache.getInstance(element).isBuiltin(element))
    }
  }
}
