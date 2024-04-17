// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.terminal.exp.completion.TerminalShellSupport
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptModel
import org.jetbrains.plugins.terminal.util.ShellType
import kotlin.math.min

internal class TerminalPromptFileViewProviderFactory : FileViewProviderFactory {
  override fun createFileViewProvider(file: VirtualFile, language: Language?, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider {
    return TerminalPromptFileViewProvider(manager, file, eventSystemEnabled)
  }
}

internal class TerminalPromptFileViewProvider(
  psiManager: PsiManager,
  virtualFile: VirtualFile,
  eventSystemEnabled: Boolean
) : SingleRootFileViewProvider(psiManager, virtualFile, eventSystemEnabled, TerminalPromptLanguage) {
  private val shellType: ShellType
    get() = virtualFile.getUserData(ShellType.KEY)!!
  private val promptModel: TerminalPromptModel
    get() = virtualFile.getUserData(TerminalPromptModel.KEY)!!

  override fun createFile(lang: Language): PsiFile {
    return TerminalPromptPsiFile(TerminalPromptFileElementType(), this)
  }

  override fun createCopy(copy: VirtualFile): SingleRootFileViewProvider {
    copy.putUserData(ShellType.KEY, shellType)
    copy.putUserData(TerminalPromptModel.KEY, promptModel)
    return TerminalPromptFileViewProvider(manager, copy, false)
  }

  /**
   * The element type with a custom implementation of [doParseContents],
   * that is building the [ASTNode] of two parts:
   * 1. Prompt part - it is always a plain text
   * 2. Input part - the custom language for command input
   *
   * The traditional approach with creating the [ASTNode] using lexer and parser can't be applied in our case,
   * because there is no way to supply the information about the input language and the input start offset
   * to the [com.intellij.lang.ParserDefinition].
   */
  private inner class TerminalPromptFileElementType : IFileElementType("TERMINAL_PROMPT_FILE", TerminalPromptLanguage, false) {
    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
      val inputOffset = min(promptModel.commandStartOffset, chameleon.chars.length)
      val inputElementType = TerminalShellSupport.findByShellType(shellType)?.promptContentElementType
                             ?: PlainTextTokenTypes.PLAIN_TEXT_FILE
      val promptNode = LazyParseablePsiElement(PlainTextTokenTypes.PLAIN_TEXT_FILE, chameleon.chars.subSequence(0, inputOffset))
      val inputNode = LazyParseablePsiElement(inputElementType, chameleon.chars.subSequence(inputOffset, chameleon.chars.length))

      val root = CompositeElement(CONTENT_ELEMENT_TYPE)
      root.psi = TerminalPromptContentElement(root)
      root.rawAddChildrenWithoutNotifications(promptNode)
      root.rawAddChildrenWithoutNotifications(inputNode)
      return root
    }
  }

  private class TerminalPromptContentElement(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun toString(): String {
      return javaClass.simpleName
    }
  }

  companion object {
    private val CONTENT_ELEMENT_TYPE: IElementType = IElementType("TERMINAL_PROMPT_CONTENT", TerminalPromptLanguage)
  }
}