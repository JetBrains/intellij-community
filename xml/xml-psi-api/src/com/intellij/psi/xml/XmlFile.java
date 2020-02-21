// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.psi.FileResolveScopeProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface XmlFile extends PsiFile, XmlElement, FileResolveScopeProvider {
  XmlFile[] EMPTY_ARRAY = new XmlFile[0];

  @Nullable
  XmlDocument getDocument();

  @Nullable
  XmlTag getRootTag();
}
