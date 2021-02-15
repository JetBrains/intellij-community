// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.formatting;

import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public final class PropertiesFormattingModelBuilder implements FormattingModelBuilder {
  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    final ASTNode root = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(formattingContext.getPsiElement()));
    PsiFile containingFile = formattingContext.getContainingFile();
    final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(containingFile);
    return new PsiBasedFormattingModel(containingFile, new PropertiesRootBlock(root, formattingContext.getCodeStyleSettings()),
                                       documentModel);
  }

}
