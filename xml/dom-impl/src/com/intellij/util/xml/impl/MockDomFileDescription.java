// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockDomFileDescription<T> extends DomFileDescription<T> {
  private final VirtualFile myFile;

  public MockDomFileDescription(final Class<T> aClass, final String rootTagName, @Nullable VirtualFile file) {
    super(aClass, rootTagName);
    myFile = file;
  }

  @Override
  public boolean isMyFile(final @NotNull XmlFile xmlFile, final Module module) {
    return xmlFile.getViewProvider().getVirtualFile().equals(myFile);
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  @Override
  public boolean isAutomaticHighlightingEnabled() {
    return false;
  }
}
