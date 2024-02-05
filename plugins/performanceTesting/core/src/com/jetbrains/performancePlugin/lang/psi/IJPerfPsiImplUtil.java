package com.jetbrains.performancePlugin.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class IJPerfPsiImplUtil {
  public static String getName(@NotNull IJPerfCommandName commandName) {
    return commandName.getNode().getText();
  }

  public static PsiElement setName(@NotNull IJPerfCommandName commandName, @NotNull String name) {
    return IJPerfElementFactory.createCommandName(commandName.getProject(), name);
  }

  public static PsiElement getNameIdentifier(@NotNull IJPerfCommandName commandName) {
    ASTNode commandNode = commandName.getNode();
    if (commandNode != null) {
      return commandNode.getPsi();
    }
    return null;
  }
}
