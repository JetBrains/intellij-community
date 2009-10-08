package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A sane usage group base.
 * Copied from com.intellij.lang.javascript.findUsages.JavaScriptGroupRuleProviderBase;
 * probably could be factored out to com.intellij.usages level.
 * User: dcheryasov
 * Date: Oct 7, 2009 6:37:05 PM
 */
public class PsiElementUsageGroupBase implements UsageGroup {
  protected final SmartPsiElementPointer myElementPointer;
  protected final String myName;
  protected final Icon myIcon;

  PsiElementUsageGroupBase(@NotNull PsiElement element, Icon icon) {
    myIcon = icon;
    String name = extractName(element);
    if (name == null) name = "<anonymous>";
    myName = name;
    myElementPointer = SmartPointerManager.getInstance(element.getProject()).createLazyPointer(element);
  }

  public Icon getIcon(boolean isOpen) {
    return myIcon;
  }

  @Nullable
  protected static String extractName(PsiElement element) {
    if (element instanceof PsiFile) {
      VirtualFile vfile = ((PsiFile)element).getVirtualFile();
      if (vfile != null) return vfile.getName();
    }
    else if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PyReferenceExpression) {
      return ((PyReferenceExpression)element).getReferencedName();
    }
    return null;
  }

  @Nullable
  protected NavigatablePsiElement getNavigatable() {
    PsiElement element = getElement();
    if (element instanceof NavigatablePsiElement) {
      return (NavigatablePsiElement)element;
    }
    return null;
  }

  public PsiElement getElement() {
    return myElementPointer.getElement();
  }

  @NotNull
  public String getText(UsageView view) {
    return myName;
  }

  public FileStatus getFileStatus() {
    NavigatablePsiElement nav = getNavigatable();
    return nav != null && isValid()? nav.getFileStatus() : null;
  }

  public boolean isValid() {
    final PsiElement element = getElement();
    return element != null && element.isValid();
  }

  public void navigate(boolean focus) throws UnsupportedOperationException {
    NavigatablePsiElement nav = getNavigatable();
    if (nav != null && canNavigate()) {
      nav.navigate(focus);
    }
  }

  public boolean canNavigate() {
    return isValid();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public void update() {
  }

  public int compareTo(final UsageGroup o) {
    return myName.compareTo(((PsiElementUsageGroupBase)o).myName);
  }

  public boolean equals(final Object obj) {
    if (!(obj instanceof PsiElementUsageGroupBase)) return false;
    PsiElementUsageGroupBase group = (PsiElementUsageGroupBase)obj;
    if (isValid() && group.isValid()) {
      return getElement().getManager().areElementsEquivalent(getElement(), group.getElement());
    }
    return Comparing.equal(myName, ((PsiElementUsageGroupBase)obj).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }

}
