package com.jetbrains.python.ast

import com.intellij.lang.ASTNode
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import com.jetbrains.python.ast.impl.PyUtilCore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyAstElement : NavigatablePsiElement {
  fun <T : PyAstElement> childrenToPsi(filterSet: TokenSet?, array: Array<T>): Array<T> =
    PyPsiUtilsCore.nodesToPsi<T>(node.getChildren(filterSet), array)

  fun <T : PyAstElement> childToPsi(filterSet: TokenSet?, index: Int): T? =
    node.getChildren(filterSet).getOrNull(index)?.psi as T

  fun <T : PyAstElement> childToPsi(elType: IElementType): T? =
    node.findChildByType(elType)?.psi as T?

  fun <T : PyAstElement> childToPsi(elTypes: TokenSet): T? =
    node.findChildByType(elTypes)?.psi as T?

  fun <T : PyAstElement> childToPsiNotNull(filterSet: TokenSet?, index: Int): T =
    childToPsi<PyAstElement>(filterSet, index) as T? ?: throw RuntimeException("child must not be null: expression text $text")

  fun <T : PyAstElement> childToPsiNotNull(elType: IElementType): T =
    childToPsi<PyAstElement>(elType) as T? ?: throw RuntimeException("child must not be null; expression text $text")

  fun <E : PsiElement?> getStubOrPsiParentOfType(parentClass: Class<E?>): E? =
    PsiTreeUtil.getParentOfType<E>(this, parentClass)

  @ApiStatus.Internal
  fun acceptPyVisitor(pyVisitor: PyAstElementVisitor): Unit = pyVisitor.visitPyElement(this)
}

@ApiStatus.Experimental
fun <T> PsiElement.findChildByClass(aClass: Class<T>): T? {
  var cur = firstChild
  while (cur != null) {
    if (aClass.isInstance(cur)) return aClass.cast(cur)
    cur = cur.nextSibling
  }
  return null
}

@ApiStatus.Experimental
fun <T> PsiElement.findChildrenByClass(aClass: Class<T>): Array<T> {
  val result: java.util.ArrayList<T> = java.util.ArrayList()
  var cur: PsiElement? = firstChild
  while (cur != null) {
    if (aClass.isInstance(cur)) result.add(cur as T)
    cur = cur.nextSibling
  }
  return result.toArray(ArrayUtil.newArray(aClass, result.size))
}

@ApiStatus.Experimental
fun <T> PsiElement.findNotNullChildByClass(aClass: Class<T>): T {
  return nonNullChild(findChildByClass(aClass))
}

@ApiStatus.Experimental
fun <T : PsiElement> PsiElement.findChildByType(elementType: IElementType): T? {
  val node = node.findChildByType(elementType)
  @Suppress("UNCHECKED_CAST")
  return node?.psi as T?
}

@ApiStatus.Experimental
fun <T : PsiElement> PsiElement.findChildByTypeNotNull(elementType: IElementType): T {
  return nonNullChild(findChildByType(elementType))
}

@ApiStatus.Experimental
fun <T : PsiElement> PsiElement.findChildrenByType(elementType: TokenSet): List<T> {
  return findChildrenByType(elementType::contains)
}

@ApiStatus.Experimental
fun <T : PsiElement> PsiElement.findChildrenByType(elementType: IElementType): List<T> {
  return findChildrenByType { elementType === it }
}

private inline fun <T : PsiElement> PsiElement.findChildrenByType(filter: (IElementType) -> Boolean): List<T> {
  var result: MutableList<T>? = null
  var child: ASTNode? = node.firstChildNode
  while (child != null) {
    if (filter(child.elementType)) {
      if (result === null) {
        result = ArrayList()
      }
      result.add(child.psi as T)
    }
    child = child.treeNext
  }
  return result ?: emptyList()
}

private inline fun <T> PsiElement.nonNullChild(child: T?): T {
  return requireNotNull(child) { "child must not be null: expression text " + text }
}
