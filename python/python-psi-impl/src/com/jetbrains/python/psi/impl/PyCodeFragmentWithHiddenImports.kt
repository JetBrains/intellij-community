package com.jetbrains.python.psi.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor

class PyCodeFragmentWithHiddenImports @JvmOverloads constructor(
  project: Project,
  name: String,
  text: CharSequence,
  isPhysical: Boolean,
  supportsHiddenImports: Boolean = false,
) : PyExpressionCodeFragmentImpl(project, name, text, isPhysical) {

  private var myPseudoImportsFragment =
    if (supportsHiddenImports)
      PyCodeFragmentWithHiddenImports(project, "imports.py", "", isPhysical, supportsHiddenImports = false).also {
        it.context = super.getContext()
        super.setContext(it)
      }
    else null

  /**
   * @return the file where imports should be placed. Either `this` or hidden file for imports
   */
  fun getImportContext(): PyCodeFragmentWithHiddenImports = myPseudoImportsFragment ?: this
  override fun getRealContext(): PsiElement? = getImportContext().context

  override fun setContext(context: PsiElement?) {
    if (myPseudoImportsFragment != null) {
      myPseudoImportsFragment!!.context = context
    } else {
      super.setContext(context)
    }
  }

  override fun processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement?,
    place: PsiElement,
  ): Boolean {
    myPseudoImportsFragment?.processDeclarations(processor, state, lastParent, place)
    return super.processDeclarations(processor, state, lastParent, place)
  }

  override fun clone(): PyCodeFragmentWithHiddenImports {
    val clone = super.clone() as PyCodeFragmentWithHiddenImports
    clone.myPseudoImportsFragment = myPseudoImportsFragment?.clone()
    clone.context = clone.myPseudoImportsFragment ?: clone.context
    return clone
  }
}