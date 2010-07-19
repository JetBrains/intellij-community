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
package com.intellij.psi;

import com.intellij.lang.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.LightPsiFileImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CompositeLanguageFileViewProvider extends SingleRootFileViewProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.CompositeLanguageFileViewProvider");
  private final ConcurrentHashMap<Language, PsiFile> myRoots = new ConcurrentHashMap<Language, PsiFile>(1, ConcurrentHashMap.DEFAULT_LOAD_FACTOR, 1);
  private Set<Language> myRelevantLanguages;

  @NotNull
  public Set<Language> getLanguages() {
    if (myRelevantLanguages != null) return myRelevantLanguages;
    Set<Language> relevantLanguages = new HashSet<Language>();
    final Language baseLanguage = getBaseLanguage();
    relevantLanguages.add(baseLanguage);
    relevantLanguages.addAll(myRoots.keySet());
    return myRelevantLanguages = new LinkedHashSet<Language>(relevantLanguages);
  }

  public void contentsSynchronized() {
    super.contentsSynchronized();
    myRelevantLanguages = null;
  }

  private final Set<PsiFile> myRootsInUpdate = new HashSet<PsiFile>(4);

  public CompositeLanguageFileViewProvider(final PsiManager manager, final VirtualFile virtualFile, final boolean physical) {
    super(manager, virtualFile, physical);
  }

  protected CompositeLanguageFileViewProvider(@NotNull PsiManager manager,
                                              @NotNull VirtualFile virtualFile,
                                              boolean physical,
                                              @NotNull Language language) {
    super(manager, virtualFile, physical, language);
  }

  @NotNull
  public SingleRootFileViewProvider createCopy(final LightVirtualFile copy) {
    final CompositeLanguageFileViewProvider viewProvider = cloneInner(copy);
    final PsiFileImpl psiFile = (PsiFileImpl)viewProvider.getPsi(getBaseLanguage());
    assert psiFile != null;
    psiFile.setOriginalFile(getPsi(getBaseLanguage()));

    // copying main tree
    final FileElement treeClone = (FileElement)psiFile.calcTreeElement().clone(); // base language tree clone
    psiFile.setTreeElementPointer(treeClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    treeClone.setPsi(psiFile);

    for (Map.Entry<Language, PsiFile> entry : myRoots.entrySet()) {
      final PsiFile root = entry.getValue();
      if (root != psiFile && root != null && root.getLanguage() != getBaseLanguage()) {
        if (root instanceof LightPsiFileImpl) {
          final LightPsiFileImpl lightFile = (LightPsiFileImpl)root;
          final LightPsiFileImpl clone = lightFile.copyLight(viewProvider);
          clone.setOriginalFile(root);
          viewProvider.myRoots.put(entry.getKey(), clone);
        }
        else {
          LOG.error("Only light files supported for language extensions, passed: " + root);
        }
      }
    }
    return viewProvider;
  }

  protected CompositeLanguageFileViewProvider cloneInner(VirtualFile copy) {
    return new CompositeLanguageFileViewProvider(getManager(), copy, false);
  }

  @Nullable
  protected PsiFile getPsiInner(Language target) {
    PsiFile file = super.getPsiInner(target);
    if (file != null) return file;
    file = myRoots.get(target);
    if (file == null) {
      file = createFile(target);
      if (file == null) return null;
      file = myRoots.cacheOrGet(target, file);
    }
    return file;
  }

  public PsiFile getCachedPsi(Language target) {
    if (target == getBaseLanguage()) return super.getCachedPsi(target);
    return myRoots.get(target);
  }

  public void checkAllTreesEqual() {
    final String psiText = getPsi(getBaseLanguage()).getText();
    for (Map.Entry<Language, PsiFile> entry : myRoots.entrySet()) {
      final PsiFile psiFile = entry.getValue();
      LOG.assertTrue(psiFile.getTextLength() == psiText.length(), entry.getKey().getID() + " tree text differs from base!");
      LOG.assertTrue(psiFile.getText().equals(psiText), entry.getKey().getID() + " tree text differs from base!");
    }
  }

  public FileElement[] getKnownTreeRoots() {
    final List<FileElement> knownRoots = new ArrayList<FileElement>();
    ContainerUtil.addAll(knownRoots, super.getKnownTreeRoots());
    for (PsiFile psiFile : myRoots.values()) {
      if (psiFile == null || !(psiFile instanceof PsiFileImpl)) continue;
      final FileElement fileElement = ((PsiFileImpl)psiFile).getTreeElement();
      if (fileElement == null) continue;
      knownRoots.add(fileElement);
    }
    return knownRoots.toArray(new FileElement[knownRoots.size()]);
  }

  @Nullable
  public PsiElement findElementAt(int offset, Class<? extends Language> lang) {
    final PsiFile mainRoot = getPsi(getBaseLanguage());
    PsiElement ret = null;
    for (final Language language : getLanguages()) {
      if (!ReflectionCache.isAssignable(lang, language.getClass())) continue;
      if (lang.equals(Language.class) && !getLanguages().contains(language)) continue;

      final PsiFile psiRoot = getPsi(language);
      final PsiElement psiElement = findElementAt(psiRoot, offset);
      if (psiElement == null || psiElement instanceof OuterLanguageElement) continue;
      if (ret == null || psiRoot != mainRoot) {
        ret = psiElement;
      }
    }
    return ret;
  }

  @Nullable
  public PsiElement findElementAt(int offset) {
    return findElementAt(offset, Language.class);
  }

  @Nullable
  public PsiReference findReferenceAt(int offset) {
    TextRange minRange = new TextRange(0, getContents().length());
    PsiReference ret = null;
    for (final Language language : getLanguages()) {
      final PsiElement psiRoot = getPsi(language);
      final PsiReference reference = SharedPsiElementImplUtil.findReferenceAt(psiRoot, offset);
      if (reference == null) continue;
      final TextRange textRange = reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
      if (minRange.contains(textRange)) {
        minRange = textRange;
        ret = reference;
      }
    }
    return ret;
  }

  private void checkConsistensy(final PsiFile oldFile) {
    ASTNode oldNode = oldFile.getNode();
    if (oldNode.getTextLength() != getContents().length() ||
       !oldNode.getText().equals(getContents().toString())) {
      @NonNls String message = "Check consistency failed for: " + oldFile;
      message += "\n     oldFile.getNode().getTextLength() = " + oldNode.getTextLength();
      message += "\n     getContents().length() = " + getContents().length();
      message += "\n     language = " + oldFile.getLanguage();

      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        message += "\n     oldFileText:\n" + oldNode.getText();
        message += "\n     contentsText:\n" + getContents().toString();
        message += "\n     jspText:\n" + getPsi(getBaseLanguage()).getNode().getText();
      }
      LOG.error(message);
      assert false;
    }
  }

  public LanguageFilter[] getLanguageExtensions() {
    return new LanguageFilter[0];
  }

  protected void removeFile(Language lang) {
    myRoots.remove(lang);
  }

  @Nullable
  protected PsiFile createFile(Language lang) {
    final PsiFile psiFile = super.createFile(lang);
    if (psiFile != null) return psiFile;
    if (isIgnored()) return null;

    if (isRelevantLanguage(lang)) {
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      assert parserDefinition != null;
      return parserDefinition.createFile(this);
    }
    return null;
  }

  protected boolean isRelevantLanguage(final Language lang) {
    return getLanguages().contains(lang);
  }

  public void rootChanged(PsiFile psiFile) {
    if (myRootsInUpdate.contains(psiFile)) return;
    if (psiFile.getLanguage() == getBaseLanguage()) {
      super.rootChanged(psiFile);
    }
    else if (!myRootsInUpdate.contains(getPsi(getBaseLanguage()))) {
      LOG.error("Changing PSI for aux trees is not supported");
    }
  }

  public Set<PsiFile> getRootsInUpdate() {
    return myRootsInUpdate;
  }
}
