/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class DummyHolderViewProvider extends UserDataHolderBase implements FileViewProvider{
  private DummyHolder myHolder;
  private PsiManager myManager;
  private final long myModificationStamp;
  final LightVirtualFile myLightVirtualFile = new LightVirtualFile("DummyHolder");

  public DummyHolderViewProvider(final PsiManager manager) {
    myManager = manager;
    myModificationStamp = LocalTimeCounter.currentTime();
  }

  public PsiManager getManager() {
    return myManager;
  }

  @Nullable
  public Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getVirtualFile());
  }

  @NotNull
  public CharSequence getContents() {
    return myHolder != null ? myHolder.getNode().getText() : "";
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myLightVirtualFile;
  }

  public Language getBaseLanguage() {
    return myHolder.getLanguage();
  }

  public Set<Language> getRelevantLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  public Set<Language> getPrimaryLanguages() {
    return getRelevantLanguages();
  }

  public PsiFile getPsi(Language target) {
    ((PsiManagerImpl)myManager).getFileManager().setViewProvider(getVirtualFile(), this);
    return target == getBaseLanguage() ? myHolder : null;
  }

  public void beforeContentsSynchronized() {}

  public void contentsSynchronized() {}

  public boolean isEventSystemEnabled() {
    return false;
  }

  public boolean isPhysical() {
    return false;
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void rootChanged(PsiFile psiFile) {
  }

  public void setDummyHolder(final DummyHolder dummyHolder) {
    myHolder = dummyHolder;
    //myLightVirtualFile.setContent(null, getContents(), false);
  }

  public FileViewProvider clone(){
    throw new RuntimeException("Clone is not supported for DummyHolderProviders. Use DummyHolder clone directly.");
  }

  public PsiReference findReferenceAt(final int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(getPsi(getBaseLanguage()), offset);
  }

  @Nullable
  public PsiElement findElementAt(final int offset, final Language language) {
    return language == getBaseLanguage() ? findElementAt(offset) : null;
  }


  public PsiElement findElementAt(int offset, Class<? extends Language> lang) {
    if (!lang.isAssignableFrom(getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  public PsiReference findReferenceAt(final int offsetInElement, final Language language) {
    return language == getBaseLanguage() ? findReferenceAt(offsetInElement) : null;
  }

  public Lexer createLexer(final Language language) {
    return myHolder.createLexer();
  }

  public boolean isLockedByPsiOperations() {
    return false;
  }

  public PsiElement findElementAt(final int offset) {
    final LeafElement element = ((PsiFileImpl)getPsi(getBaseLanguage())).calcTreeElement().findLeafElementAt(offset);
    return element != null ? element.getPsi() : null;
  }
}
