// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.lang.Language
import com.intellij.lang.LighterAST
import com.intellij.lang.TreeBackedLighterAST
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveState
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.CharTable
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Custom PSI implementation for the Terminal output.
 * The main purpose of it is to make it possible to determine the underlying language of the editor
 * and provide some features for the terminal specifically (like popup-completion, inline completion).
 *
 * This class overrides the [PsiFile] and [FileASTNode] interfaces manually
 * to always represent the snapshot of the terminal output document content.
 * This way, when the document changes, it is not detected as uncommitted in [com.intellij.psi.impl.PsiDocumentManagerBase].
 * And since we don't need to commit it, we avoid the write action, which benefits the terminal performance.
 * Instead, we modify the [charsSequence] on output model changes without write actions.
 *
 * Since the terminal output model is modified on EDT without write action,
 * this PSI file can be changed even under read action when accessed in the background thread.
 */
@ApiStatus.Internal
class TerminalOutputPsiFile(
  viewProvider: FileViewProvider,
  initialContent: CharSequence,
) : PsiFile, FileASTNode, UserDataHolderBase() {
  private val viewProvider: AbstractFileViewProvider = viewProvider as AbstractFileViewProvider
  private val contentElement = TerminalOutputElement(parent = this, initialContent)
  private val charTable = CharTableImpl()

  var charsSequence: CharSequence
    get() = contentElement.charsSequence
    set(value) {
      contentElement.charsSequence = value
    }

  private var originalFile: PsiFile? = null

  override fun getFileType(): FileType {
    return TerminalOutputFileType
  }

  override fun getLanguage(): Language {
    return TerminalOutputLanguage
  }

  override fun getViewProvider(): FileViewProvider {
    return viewProvider
  }

  override fun getVirtualFile(): VirtualFile {
    return viewProvider.virtualFile
  }

  override fun getModificationStamp(): Long {
    return viewProvider.modificationStamp
  }

  override fun getProject(): Project {
    return viewProvider.manager.project
  }

  override fun getManager(): PsiManager {
    return viewProvider.manager
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

  override fun getOriginalFile(): PsiFile {
    return originalFile ?: this
  }

  private fun setOriginalFile(originalFile: PsiFile) {
    val newOriginal = originalFile.originalFile
    this.originalFile = newOriginal

    val originalViewProvider = newOriginal.viewProvider as AbstractFileViewProvider
    originalViewProvider.registerAsCopy(viewProvider)
  }

  override fun copy(): PsiElement {
    val copy = clone()
    copy.setOriginalFile(this)
    return copy
  }

  override fun clone(): TerminalOutputPsiFile {
    return TerminalOutputPsiFile(viewProvider.clone(), charsSequence)
  }

  override fun findElementAt(offset: Int): PsiElement? {
    val element = contentElement
    return if (element.textRange.contains(offset)) {
      element
    }
    else null
  }

  override fun getNode(): FileASTNode {
    return this
  }

  override fun getChildren(): Array<out PsiElement> {
    return arrayOf(contentElement)
  }

  override fun getFirstChild(): PsiElement {
    return contentElement
  }

  override fun getLastChild(): PsiElement {
    return contentElement
  }

  override fun getNextSibling(): PsiElement? {
    return null
  }

  override fun getPrevSibling(): PsiElement? {
    return null
  }

  override fun getContainingFile(): PsiFile {
    return this
  }

  override fun getTextRange(): TextRange {
    return contentElement.textRange
  }

  override fun getStartOffsetInParent(): Int {
    return 0
  }

  override fun getTextLength(): Int {
    return contentElement.textLength
  }

  override fun findReferenceAt(offset: Int): PsiReference? {
    return null
  }

  override fun getTextOffset(): Int {
    return 0
  }

  override fun getText(): @NlsSafe String {
    return contentElement.text
  }

  override fun textToCharArray(): CharArray {
    return contentElement.textToCharArray()
  }

  override fun textMatches(text: @NonNls CharSequence): Boolean {
    return contentElement.textMatches(text)
  }

  override fun textMatches(element: PsiElement): Boolean {
    return contentElement.textMatches(element)
  }

  override fun textContains(c: Char): Boolean {
    return contentElement.textContains(c)
  }

  override fun accept(visitor: PsiElementVisitor) {
    visitor.visitFile(this)
  }

  override fun acceptChildren(visitor: PsiElementVisitor) {
    visitor.visitElement(contentElement)
  }

  override fun subtreeChanged() {
    // do nothing
  }

  override fun getNavigationElement(): PsiElement? {
    return null
  }

  override fun getOriginalElement(): PsiElement {
    return this
  }

  override fun getReference(): PsiReference? {
    return null
  }

  override fun getReferences(): Array<out PsiReference> {
    return emptyArray()
  }

  override fun processChildren(processor: PsiElementProcessor<in PsiFileSystemItem>): Boolean {
    return true
  }

  override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
    return true
  }

  override fun getContext(): PsiElement? {
    return null
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

  override fun getContainingDirectory(): PsiDirectory? {
    return null
  }

  override fun isDirectory(): Boolean {
    return false
  }

  override fun getName(): @NlsSafe String {
    return "TerminalOutput"
  }

  override fun getParent(): PsiDirectory? {
    return null
  }

  override fun getIcon(flags: Int): Icon? {
    return null
  }

  override fun getPresentation(): ItemPresentation? {
    return null
  }

  @Deprecated("Deprecated in PsiFile interface")
  override fun getPsiRoots(): Array<out PsiFile> {
    return arrayOf(this)
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

  override fun checkSetName(name: String?) {
    throw IncorrectOperationException()
  }

  override fun setName(name: @NlsSafe String): PsiElement {
    throw IncorrectOperationException()
  }

  //--------------------------------- FileASTNode implementation ---------------------------------

  override fun getChildren(filter: TokenSet?): Array<out ASTNode> {
    return if (filter == null || filter.contains(contentElement.elementType)) arrayOf(contentElement) else emptyArray()
  }

  override fun getCharTable(): CharTable {
    return charTable
  }

  override fun isParsed(): Boolean {
    return true
  }

  override fun getLighterAST(): LighterAST {
    return TreeBackedLighterAST(this)
  }

  override fun getElementType(): IElementType {
    return TerminalOutputTokenTypes.FILE
  }

  override fun getChars(): CharSequence {
    return contentElement.chars
  }

  override fun getStartOffset(): Int {
    return 0
  }

  override fun getTreeParent(): ASTNode? {
    return null
  }

  override fun getFirstChildNode(): ASTNode {
    return contentElement
  }

  override fun getLastChildNode(): ASTNode {
    return contentElement
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
    return copy().node
  }

  override fun findLeafElementAt(offset: Int): ASTNode? {
    val element = contentElement
    return if (element.textRange.contains(offset)) {
      element
    }
    else null
  }

  override fun findChildByType(type: IElementType): ASTNode? {
    if (contentElement.elementType == type) {
      return contentElement
    }
    return null
  }

  override fun findChildByType(type: IElementType, anchor: ASTNode?): ASTNode? {
    if (anchor != null) return null
    return findChildByType(type)
  }

  override fun findChildByType(typesSet: TokenSet): ASTNode? {
    if (typesSet.contains(contentElement.elementType)) {
      return contentElement
    }
    return null
  }

  override fun findChildByType(typesSet: TokenSet, anchor: ASTNode?): ASTNode? {
    if (anchor != null) return null
    return findChildByType(typesSet)
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