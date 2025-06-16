package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.*

/**
 * Appends missing `return None`, and transforms `return` into `return None`.
 */
class PyMakeReturnExplicitFix(statement: PyStatement) : PsiUpdateModCommandAction<PyStatement>(statement) {
  override fun getFamilyName(): @IntentionFamilyName String = PyPsiBundle.message("QFIX.add.explicit.return.none")

  override fun getPresentation(context: ActionContext, element: PyStatement): Presentation? {
    if (element is PyReturnStatement || element is PyPassStatement) {
      return Presentation.of(PyPsiBundle.message("QFIX.replace.with.return.none"))
    }
    return super.getPresentation(context, element)
  }
  
  override fun invoke(context: ActionContext, element: PyStatement, updater: ModPsiUpdater) {
    val elementGenerator = PyElementGenerator.getInstance(element.getProject())
    val languageLevel = LanguageLevel.forElement(element)

    val returnStmt = elementGenerator.createFromText(languageLevel, PyReturnStatement::class.java, "return None")

    if (element is PyReturnStatement || element is PyPassStatement) {
      element.replace(returnStmt)
    }
    else {
      element.parent.addAfter(returnStmt, element)
    }
  }
}
