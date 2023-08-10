/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myText;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement() {
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