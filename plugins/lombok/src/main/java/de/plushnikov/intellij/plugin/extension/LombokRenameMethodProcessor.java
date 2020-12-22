package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * RenameProcessor for replacement of lombok virtual methods/fields with root elements
 */
public class LombokRenameMethodProcessor extends RenamePsiElementProcessor {

  public boolean canProcessElement(@NotNull PsiElement elem) {
    return (elem instanceof LombokLightMethodBuilder || elem instanceof LombokLightFieldBuilder)
      && !(elem.getNavigationElement() instanceof PsiAnnotation);
  }

  @Nullable
  public PsiElement substituteElementToRename(@NotNull PsiElement elem, @Nullable Editor editor) {
    return elem.getNavigationElement();
  }
}
