package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class LombokRenameReferenceProcessor extends RenameJavaVariableProcessor {
  @Override
  public boolean canProcessElement(PsiElement element) {
    return super.canProcessElement(element);
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element, boolean searchInCommentsAndStrings) {
    return super.findReferences(element, searchInCommentsAndStrings);
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    return super.findReferences(element);
  }
}
