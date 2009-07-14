package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.MoveDestination;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public interface MoveClassHandler {
  ExtensionPointName<MoveClassHandler> EP_NAME = new ExtensionPointName<MoveClassHandler>("com.intellij.refactoring.moveClassHandler");

  /**
   * @return null if it cannot move aClass
   */
  @Nullable
  PsiClass doMoveClass(@NotNull PsiClass aClass, @NotNull MoveDestination moveDestination) throws IncorrectOperationException;

  /**
   *
   * @param clazz psiClass
   * @return null, if this instance of FileNameForPsiProvider cannot provide name for clazz
   */
  String getName(PsiClass clazz); 
}
