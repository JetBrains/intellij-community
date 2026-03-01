package org.jetbrains.plugins.terminal.sh.prompt

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sh.ShLanguage
import com.intellij.sh.psi.ShCommandsList
import com.intellij.sh.psi.ShFileElementType
import com.intellij.sh.psi.ShSimpleCommand
import com.intellij.util.execution.ParametersListUtil
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

    // Handle the case for running commands like `ls <filepath>;`.
    // Shell script treats it as command termination; however, in this case, it might be a continuation of the argument.
    val nextSemicolon = PsiTreeUtil.skipWhitespacesForward(lastCommand)?.text?.takeIf { it == ";" } ?: ""
    val commandText = lastCommand.text + nextSemicolon

    return ParametersListUtil.parse(commandText, true, true, false)
  }
}
