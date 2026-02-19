package com.jetbrains.python.ast

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun <T> PsiElement.findChildByClass(aClass: Class<T>): T? {
  var cur = getFirstChild()
  while (cur != null) {
    if (aClass.isInstance(cur)) return aClass.cast(cur)
    cur = cur.getNextSibling()
  }
  return null
}

@ApiStatus.Experimental
fun <T> PsiElement.findChildrenByClass(aClass: Class<T>): Array<T> {
  val result: java.util.ArrayList<T> = java.util.ArrayList()
  var cur: PsiElement? = getFirstChild()
  while (cur != null) {
    if (aClass.isInstance(cur)) result.add(cur as T)
    cur = cur.getNextSibling()
  }
  return result.toArray(ArrayUtil.newArray(aClass, result.size))
}

@ApiStatus.Experimental
fun <T> PsiElement.findNotNullChildByClass(aClass: Class<T>): T {
  return nonNullChild(findChildByClass(aClass))
}

@ApiStatus.Experimental
fun <T : PsiElement> PsiElement.findChildByType(elementType: IElementType): T? {
  val node = getNode().findChildByType(elementType)
  @Suppress("UNCHECKED_CAST")
  return node?.getPsi() as T?
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
  var child: ASTNode? = getNode().getFirstChildNode()
  while (child != null) {
    if (filter(child.getElementType())) {
      if (result === null) {
        result = ArrayList()
      }
      result.add(child.getPsi() as T)
    }
    child = child.getTreeNext()
  }
  return result ?: emptyList()
}

private inline fun <T> PsiElement.nonNullChild(child: T?): T {
  return requireNotNull(child) { "child must not be null: expression text " + getText() }
}
