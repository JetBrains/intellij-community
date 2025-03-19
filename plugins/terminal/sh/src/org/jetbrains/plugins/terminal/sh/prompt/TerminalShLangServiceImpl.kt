package org.jetbrains.plugins.terminal.sh.prompt

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.ShLanguage
import com.intellij.sh.psi.ShCommandsList
import com.intellij.sh.psi.ShFileElementType
import com.intellij.sh.psi.ShSimpleCommand
import org.jetbrains.plugins.terminal.block.shellSupport.TerminalShLangService

internal class TerminalShLangServiceImpl : TerminalShLangService {
  override val promptContentElementType: IElementType
    get() = ShFileElementType.INSTANCE

  /**
   * @return the token list for the last shell command in [command] text
   */
  override fun getShellCommandTokens(project: Project, command: String): List<String>? {
    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(ShLanguage.INSTANCE, command)
    val commands = PsiTreeUtil.getChildrenOfType(psiFile, ShCommandsList::class.java)?.lastOrNull() ?: return null
    val lastCommand = commands.commandList.lastOrNull { it is ShSimpleCommand } ?: return null
    val tokens = mutableListOf<String>()
    // Append trailing error elements to the previous token.
    // It is the case for Windows with paths delimited by `\`.
    // Shell Script treats the trailing `\` as an error element, while it should be appended to the last token:
    // `cd src\` -> `src` and `\` are separate tokens, but should be single.
    for (literal in lastCommand.children) {
      val text = literal.text
      if (literal is PsiErrorElement && tokens.isNotEmpty()) {
        tokens[tokens.lastIndex] = tokens.last() + text
      }
      else tokens.add(text)
    }

    // Appends ';' symbol to the last command
    // It is the case for running commands like `ls <filepath>;`.
    // Shell script treats it as command termination; however, in this case, it might be a continuation of the argument.
    val nextSibling = PsiTreeUtil.skipWhitespacesForward(lastCommand) ?: return tokens
    if (nextSibling.text == ";" && tokens.isNotEmpty()) {
      tokens[tokens.lastIndex] = tokens.last() + nextSibling.text
    }
    return tokens
  }
}
