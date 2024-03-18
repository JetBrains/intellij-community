// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;

import java.util.ArrayList;
import java.util.List;

public class YAMLFileImpl extends PsiFileBase implements YAMLFile {
  public YAMLFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, YAMLLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return YAMLFileType.YML;
  }

  @Override
  public String toString() {
    return "YAML file: " + getName();
  }

  @Override
  public List<YAMLDocument> getDocuments() {
    final ArrayList<YAMLDocument> result = new ArrayList<>();
    for (ASTNode node : getNode().getChildren(TokenSet.create(YAMLElementTypes.DOCUMENT))) {
     result.add((YAMLDocument) node.getPsi());
    }
    return result;
  }
}
