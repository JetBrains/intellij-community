// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import static com.intellij.psi.impl.source.resolve.reference.impl.CachingReference.getManipulator;

public abstract class MavenPsiReference implements PsiReference {
  protected final @NotNull Project myProject;
  protected final @NotNull MavenProjectsManager myProjectsManager;

  protected final @NotNull PsiFile myPsiFile;
  protected final @NotNull VirtualFile myVirtualFile;

  protected final @NotNull PsiElement myElement;
  protected final @NotNull String myText;
  protected @NotNull TextRange myRange;

  public MavenPsiReference(@NotNull PsiElement element, @NotNull String text, @NotNull TextRange range) {
    myProject = element.getProject();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);

    myPsiFile = element.getContainingFile().getOriginalFile();
    myVirtualFile = myPsiFile.getVirtualFile();

    myElement = element;
    myText = text;
    myRange = range;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myText;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return getElement().getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    TextRange rangeInElement = getRangeInElement();
    PsiElement element = manipulator.handleContentChange(getElement(), rangeInElement, newElementName);
    myRange = new TextRange(rangeInElement.getStartOffset(), rangeInElement.getStartOffset() + newElementName.length());
    return element;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}