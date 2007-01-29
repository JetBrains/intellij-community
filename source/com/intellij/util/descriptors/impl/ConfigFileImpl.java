/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.util.descriptors.impl;

import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileMetaData;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.deployment.VerificationException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ConfigFileImpl implements ConfigFile {
  private final @NotNull ConfigFileInfo myInfo;
  private VirtualFilePointer myFilePointer;
  private PsiFile myPsiFile;
  private final ConfigFileContainerImpl myContainer;
  private final Project myProject;
  private long myModificationCount;

  public ConfigFileImpl(final @NotNull ConfigFileContainerImpl container, @NotNull final ConfigFileInfo configuration) {
    myContainer = container;
    myInfo = configuration;
    setUrl(configuration.getUrl());
    Disposer.register(container, this);
    myProject = myContainer.getProject();
  }

  public void setUrl(String url) {
    final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    if (myFilePointer != null) {
      pointerManager.kill(myFilePointer);
    }
    myFilePointer = pointerManager.create(url, new VirtualFilePointerListener() {
      public void beforeValidityChanged(final VirtualFilePointer[] pointers) {
      }

      public void validityChanged(final VirtualFilePointer[] pointers) {
        myPsiFile = null;
        onChange();
      }
    });
    onChange();
  }

  private void onChange() {
    myModificationCount++;
    myContainer.fireDescriptorChanged(this);
  }

  public String getUrl() {
    return myFilePointer.getUrl();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return myFilePointer.getFile();
  }

  @Nullable
  public PsiFile getPsiFile() {
    if (myPsiFile != null && myPsiFile.isValid()) {
      return myPsiFile;
    }

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;

    myPsiFile = PsiManager.getInstance(myProject).findFile(virtualFile);

    return myPsiFile;
  }

  @Nullable
  public XmlFile getXmlFile() {
    return (XmlFile)getPsiFile();
  }

  public void dispose() {
    VirtualFilePointerManager.getInstance().kill(myFilePointer);
  }

  @NotNull
  public ConfigFileInfo getInfo() {
    return myInfo;
  }

  public boolean isValid() {
    final PsiFile psiFile = getPsiFile();
    if (psiFile == null || !psiFile.isValid()) {
      return false;
    }
    if (psiFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)psiFile).getDocument();
      return document != null && document.getRootTag() != null;
    }
    return true;
  }


  @NotNull
  public ConfigFileMetaData getMetaData() {
    return myInfo.getMetaData();
  }


  public long getModificationCount() {
    return myModificationCount;
  }
}
