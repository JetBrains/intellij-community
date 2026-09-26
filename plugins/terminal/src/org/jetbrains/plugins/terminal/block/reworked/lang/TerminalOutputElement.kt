// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.IncorrectOperationException
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Represents the content of the [TerminalOutputPsiFile].
 * Which should always represent the current state of the terminal output model
 * that is guaranteed by updating [charsSequence] property on output model changes.
 *
 * Since the terminal output model is modified on EDT without write action,
 * this PSI element can be changed even under read action when accessed in the background thread.
 */
@ApiStatus.Internal
class TerminalOutputElement(
  private val parent: PsiFile,
  var charsSequence: CharSequence,
) : PsiElement, ASTNode, UserDataHolderBase() {
  override fun getProject(): Project {
    return parent.project
  }

  override fun getLanguage(): Language {
    return TerminalOutputLanguage
  }

  override fun getManager(): PsiManager? {
    return parent.manager
  }

  override fun getParent(): PsiElement {
    return parent
  }

  override fun getContainingFile(): PsiFile {
    return parent
  }

  override fun isValid(): Boolean {
    return true
  }

  override fun isWritable(): Boolean {
    return true
  }

  override fun isPhysical(): Boolean {
    return false
  }

  override fun getNode(): ASTNode {
    return this
  }

  override fun copy(): TerminalOutputElement {
    return clone()
  }

  override fun clone(): TerminalOutputElement {
    return TerminalOutputElement(parent, charsSequence)
  }

  override fun getTextRange(): TextRange {
    return TextRange(0, charsSequence.length)
  }

  override fun getStartOffsetInParent(): Int {
    return 0
  }

  override fun getTextLength(): Int {
    return charsSequence.length
  }

  override fun getTextOffset(): Int {
    return 0
  }

  override fun getText(): @NlsSafe String {
    return charsSequence.toString()
  }

  override fun textToCharArray(): CharArray {
    return CharArrayUtil.fromSequence(charsSequence)
  }

  override fun textMatches(text: @NonNls CharSequence): Boolean {
    return charsSequence == text
  }

  override fun textMatches(element: PsiElement): Boolean {
    return textMatches(element.text)
  }

  override fun textContains(c: Char): Boolean {
    val sequence = charsSequence
    val chars = CharArrayUtil.fromSequenceWithoutCopying(sequence)
    if (chars != null) {
      for (aChar in chars) {
        if (aChar == c) return true
      }
      return false
    }
    return sequence.contains(c)
  }

  override fun findElementAt(offset: Int): PsiElement {
    return this
  }

  override fun findReferenceAt(offset: Int): PsiReference? {
    return null
  }

  override fun getNavigationElement(): PsiElement? {
    return null
  }

  override fun getOriginalElement(): PsiElement {
    return this
  }

  override fun accept(visitor: PsiElementVisitor) {
    visitor.visitElement(this)
  }

  override fun acceptChildren(visitor: PsiElementVisitor) {
  }

  override fun getReference(): PsiReference? {
    return null
  }

  override fun getReferences(): Array<out PsiReference> {
    return emptyArray()
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
    return true
  }

  override fun getContext(): PsiElement {
    return parent
  }

  override fun getResolveScope(): GlobalSearchScope {
    return GlobalSearchScope.EMPTY_SCOPE
  }

  override fun getUseScope(): SearchScope {
    return GlobalSearchScope.EMPTY_SCOPE
  }

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return this === another
  }

  override fun getIcon(flags: Int): Icon? {
    return null
  }

  override fun getChildren(): Array<out PsiElement> {
    return emptyArray()
  }

  override fun getFirstChild(): PsiElement? {
    return null
  }

  override fun getLastChild(): PsiElement? {
    return null
  }

  override fun getNextSibling(): PsiElement? {
    return null
  }

  override fun getPrevSibling(): PsiElement? {
    return null
  }

  override fun add(element: PsiElement): PsiElement {
    throw IncorrectOperationException()
  }

  override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement {
    throw IncorrectOperationException()
  }

  override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement {
    throw IncorrectOperationException()
  }

  @Deprecated("Deprecated in PsiElement interface")
  override fun checkAdd(element: PsiElement) {
    throw IncorrectOperationException()
  }

  override fun addRange(first: PsiElement?, last: PsiElement?): PsiElement {
    throw IncorrectOperationException()
  }

  override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement?): PsiElement {
    throw IncorrectOperationException()
  }

  override fun addRangeAfter(first: PsiElement?, last: PsiElement?, anchor: PsiElement?): PsiElement {
    throw IncorrectOperationException()
  }

  override fun delete() {
    throw IncorrectOperationException()
  }

  @Deprecated("Deprecated in PsiElement interface")
  override fun checkDelete() {
    throw IncorrectOperationException()
  }

  override fun deleteChildRange(first: PsiElement?, last: PsiElement?) {
    throw IncorrectOperationException()
  }

  override fun replace(newElement: PsiElement): PsiElement {
    throw IncorrectOperationException()
  }

  //--------------------------------- ASTNode implementation ---------------------------------

  override fun getChildren(filter: TokenSet?): Array<out ASTNode> {
    return emptyArray()
  }

  override fun getElementType(): IElementType {
    return TerminalOutputTokenTypes.TEXT
  }

  override fun getChars(): CharSequence {
    return charsSequence
  }

  override fun getStartOffset(): Int {
    return 0
  }

  override fun getTreeParent(): ASTNode? {
    return parent.node
  }

  override fun getFirstChildNode(): ASTNode? {
    return null
  }

  override fun getLastChildNode(): ASTNode? {
    return null
  }

  override fun getTreeNext(): ASTNode? {
    return null
  }

  override fun getTreePrev(): ASTNode? {
    return null
  }

  override fun addChild(child: ASTNode) {
    throw IncorrectOperationException()
  }

  override fun addChild(child: ASTNode, anchorBefore: ASTNode?) {
    throw IncorrectOperationException()
  }

  override fun addLeaf(leafType: IElementType, leafText: CharSequence, anchorBefore: ASTNode?) {
    throw IncorrectOperationException()
  }

  override fun removeChild(child: ASTNode) {
    throw IncorrectOperationException()
  }

  override fun removeRange(firstNodeToRemove: ASTNode, firstNodeToKeep: ASTNode?) {
    throw IncorrectOperationException()
  }

  override fun replaceChild(oldChild: ASTNode, newChild: ASTNode) {
    throw IncorrectOperationException()
  }

  override fun replaceAllChildrenToChildrenOf(anotherParent: ASTNode) {
    throw IncorrectOperationException()
  }

  override fun addChildren(firstChild: ASTNode, firstChildToNotAdd: ASTNode?, anchorBefore: ASTNode?) {
    throw IncorrectOperationException()
  }

  override fun copyElement(): ASTNode {
    return clone()
  }

  override fun findLeafElementAt(offset: Int): ASTNode {
    return this
  }

  override fun findChildByType(type: IElementType): ASTNode? {
    return null
  }

  override fun findChildByType(type: IElementType, anchor: ASTNode?): ASTNode? {
    return null
  }

  override fun findChildByType(typesSet: TokenSet): ASTNode? {
    return null
  }

  override fun findChildByType(typesSet: TokenSet, anchor: ASTNode?): ASTNode? {
    return null
  }

  override fun getPsi(): PsiElement {
    return this
  }

  override fun <T : PsiElement?> getPsi(clazz: Class<T?>): T? {
    if (!clazz.isInstance(this)) {
      thisLogger().error("Unexpected PSI class. Expected: $clazz got: $this")
    }
    @Suppress("UNCHECKED_CAST")
    return this as T?
  }
}