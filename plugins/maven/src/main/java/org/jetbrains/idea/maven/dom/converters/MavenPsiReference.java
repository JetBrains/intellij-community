package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public abstract class MavenPsiReference implements PsiReference {
  protected final @NotNull Project myProject;
  protected final @NotNull MavenProjectsManager myProjectsManager;

  protected final @NotNull PsiFile myPsiFile;
  protected final @NotNull VirtualFile myVirtualFile;

  protected final @NotNull PsiElement myElement;
  protected final @NotNull String myText;
  protected final @NotNull TextRange myRange;

  public MavenPsiReference(@NotNull PsiElement element, @NotNull String text, @NotNull TextRange range) {
    myProject = element.getProject();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);

    myPsiFile = element.getContainingFile().getOriginalFile();
    myVirtualFile = myPsiFile.getVirtualFile();

    myElement = element;
    myText = text;
    myRange = range;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public String getCanonicalText() {
    return myText;
  }

  public TextRange getRangeInElement() {
    return myRange;
  }

  public boolean isReferenceTo(PsiElement element) {
    return getElement().getManager().areElementsEquivalent(element, resolve());
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isSoft() {
    return true;
  }
}