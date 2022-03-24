package com.jetbrains.python.psi.resolve

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.QualifiedName
import com.intellij.psi.util.siblings
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.ResolveResultList
import com.jetbrains.python.psi.types.TypeEvalContext

class PyIPythonBuiltinReferenceResolveProvider : PyReferenceResolveProvider {

  private val underscoreRegex = "_[0-9]*".toRegex()

  override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    if (!needToResolve(context)) return emptyList()
    val name = element.referencedName ?: return emptyList()
    if (name matches underscoreRegex) {
      return resolveOut(element, context)
    }
    when (name) {
      IPythonBuiltinConstants.DISPLAY -> return resolveDisplay(element)
      IPythonBuiltinConstants.GET_IPYTHON -> return resolveGetIPython(element)
      IPythonBuiltinConstants.IN -> return resolveIn(element, context)
      IPythonBuiltinConstants.OUT -> return resolveOut(element, context)
      IPythonBuiltinConstants.DOUBLE_UNDERSCORE -> return resolveOut(element, context)
      IPythonBuiltinConstants.TRIPLE_UNDERSCORE -> return resolveOut(element, context)
    }

     if (name in IPythonBuiltinConstants.MAGICS_LIST
         && element.parent is PyExpressionStatement
         && isAutomagicOn(element)) {
      return resolveMagics(element, context)
    }
    return emptyList()
  }

  /**
    We resolve IPython built-ins in two cases:
      1) in PyCharm PRO in Jupyter files only
      2) in DataSpell in Python files and Jupyter files
   */
  private fun needToResolve(context: TypeEvalContext): Boolean {
    val psiFile = context.origin ?: return false
    return isJupyterFile(psiFile) || (PlatformUtils.isDataSpell() && isPythonFile(psiFile))
  }

  private fun isJupyterFile(element: PsiFile): Boolean = element.virtualFile?.extension == "ipynb"

  private fun isPythonFile(element: PsiFile): Boolean = element.virtualFile?.fileType is PythonFileType

  private fun resolveDisplay(element: PyQualifiedExpression): List<RatedResolveResult> {
    val resolveContext = fromFoothold(element)
    var displayPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.DISPLAY_DOTTED_PATH_NEW),
                                           resolveContext)
    if (displayPsi == null) {
      displayPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.DISPLAY_DOTTED_PATH_OLD),
                                         resolveContext)
    }
    return ResolveResultList.to(displayPsi)
  }

  private fun resolveGetIPython(element: PyQualifiedExpression): List<RatedResolveResult> {
    val getIPythonPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.GET_IPYTHON_DOTTED_PATH),
                                              fromFoothold(element))
    return ResolveResultList.to(getIPythonPsi)
  }

  private fun resolveIn(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    val historyClass = getPyClassByDottedPath(IPythonBuiltinConstants.HISTORY_MANAGER_DOTTED_PATH,
                                            fromFoothold(element)) ?: return emptyList()
    val inHistDictPsi = historyClass.findClassAttribute(IPythonBuiltinConstants.IN_HIST_DICT, false, context)
    return ResolveResultList.to(inHistDictPsi)
  }

  private fun resolveOut(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    val historyClass = getPyClassByDottedPath(IPythonBuiltinConstants.HISTORY_MANAGER_DOTTED_PATH,
                                             fromFoothold(element)) ?: return emptyList()
    val outHistDictPsi = historyClass.findClassAttribute(IPythonBuiltinConstants.OUT_HIST_DICT, false, context)
    return ResolveResultList.to(outHistDictPsi)
  }

  private fun resolveMagics(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    val name = element.referencedName ?: return emptyList()
    val dottedPath = getMagicDottedPathString(name) ?: return emptyList()
    val magicsClass = getPyClassByDottedPath(dottedPath, fromFoothold(element)) ?: return emptyList()
    val magicPsi = magicsClass.findMethodByName(name, false, context)
    return ResolveResultList.to(magicPsi)
  }

  private fun getMagicDottedPathString(name: String): String? =
    when (name) {
      in IPythonBuiltinConstants.AUTOMAGIC_MAGICS -> IPythonBuiltinConstants.MAGIC_AUTOMAGIC_DOTTED_PATH
      in IPythonBuiltinConstants.BASIC_MAGICS -> IPythonBuiltinConstants.MAGIC_BASIC_DOTTED_PATH
      in IPythonBuiltinConstants.CODE_MAGICS -> IPythonBuiltinConstants.MAGIC_CODE_DOTTED_PATH
      in IPythonBuiltinConstants.CONFIG_MAGICS -> IPythonBuiltinConstants.MAGIC_CONFIG_DOTTED_PATH
      in IPythonBuiltinConstants.EXECUTION_MAGICS -> IPythonBuiltinConstants.MAGIC_EXECUTION_DOTTED_PATH
      in IPythonBuiltinConstants.EXTENSION_MAGICS -> IPythonBuiltinConstants.MAGIC_EXTENSION_DOTTED_PATH
      in IPythonBuiltinConstants.HISTORY_MAGICS -> IPythonBuiltinConstants.MAGIC_HISTORY_DOTTED_PATH
      in IPythonBuiltinConstants.LOGGING_MAGICS -> IPythonBuiltinConstants.MAGIC_LOGGING_DOTTED_PATH
      in IPythonBuiltinConstants.NAMESPACE_MAGICS -> IPythonBuiltinConstants.MAGIC_NAMESPACE_DOTTED_PATH
      in IPythonBuiltinConstants.OS_MAGICS -> IPythonBuiltinConstants.MAGIC_OS_DOTTED_PATH
      in IPythonBuiltinConstants.PACKAGING_MAGICS -> IPythonBuiltinConstants.MAGIC_PACKAGING_DOTTED_PATH
      in IPythonBuiltinConstants.PYLAB_MAGICS -> IPythonBuiltinConstants.MAGIC_PYLAB_DOTTED_PATH
      in IPythonBuiltinConstants.ASYNC_MAGICS -> IPythonBuiltinConstants.MAGIC_ASYNC_DOTTED_PATH
      else -> null
    }

  private fun getPyClassByDottedPath(dottedPath: String, context: PyQualifiedNameResolveContext): PyClass? =
    resolveTopLevelMember(QualifiedName.fromDottedString(dottedPath), context) as? PyClass

  private fun isAutomagicOn(element: PyQualifiedExpression): Boolean {
    val previousSiblings = element.parent.siblings(forward = false, withSelf = false)
    for (sib in previousSiblings) {
      if (sib is PyEmptyExpression) {
        if (sib.text == "%automagic 0") {
          return false
        }
        else if (sib.text == "%automagic 1") {
          return true
        }
      }
      if (sib is PyExpressionStatement
          && sib.nextSibling is PsiWhiteSpace
          && sib.children.let {
            it.size == 2 && it.first() is PyReferenceExpression && it.last() is PsiErrorElement && it.first().text == "automagic"
          }
          && sib.nextSibling.nextSibling.let {
            it is PyExpressionStatement && it.children.singleOrNull() is PyNumericLiteralExpression
          }
      ) {
        if (sib.nextSibling.nextSibling.text == "1") {
          return true
        }
        if (sib.nextSibling.nextSibling.text == "0") {
          return false
        }
      }
    }
    return true
  }
}

