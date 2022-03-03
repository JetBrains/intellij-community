package com.jetbrains.python.psi.resolve

import com.intellij.psi.PsiFile
import com.intellij.psi.util.QualifiedName
import com.intellij.util.PlatformUtils
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyQualifiedExpression
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
    val displayPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.DISPLAY_DOTTED_PATH),
                                           fromFoothold(element))
    return ResolveResultList.to(displayPsi)
  }

  private fun resolveGetIPython(element: PyQualifiedExpression): List<RatedResolveResult> {
    val getIPythonPsi = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.GET_IPYTHON_DOTTED_PATH),
                                              fromFoothold(element))
    return ResolveResultList.to(getIPythonPsi)
  }

  private fun resolveIn(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    val historyClass = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.HISTORY_MANAGER_DOTTED_PATH),
                                            fromFoothold(element)) as? PyClass ?: return emptyList()
    val inHistDictPsi = historyClass.findClassAttribute(IPythonBuiltinConstants.IN_HIST_DICT, false, context)
    return ResolveResultList.to(inHistDictPsi)
  }

  private fun resolveOut(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
    val historyClass = resolveTopLevelMember(QualifiedName.fromDottedString(IPythonBuiltinConstants.HISTORY_MANAGER_DOTTED_PATH),
                                             fromFoothold(element)) as? PyClass ?: return emptyList()
    val outHistDictPsi = historyClass.findClassAttribute(IPythonBuiltinConstants.OUT_HIST_DICT, false, context)
    return ResolveResultList.to(outHistDictPsi)
  }
}

