/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.properties.formatting;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesFormattingModelBuilder implements FormattingModelBuilder {
  @NotNull
  @Override
  public PropertiesFormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    final ASTNode root = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
    final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(element.getContainingFile());
    return new PropertiesFormattingModel(root, documentModel, settings);
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  private static class PropertiesFormattingModel implements FormattingModel {
    private final FormattingDocumentModelImpl myDocumentModel;
    private PropertiesRootBlock myRoot;

    public PropertiesFormattingModel(ASTNode root, FormattingDocumentModelImpl documentModel, CodeStyleSettings settings) {
      myRoot = new PropertiesRootBlock(root, null, settings);
      myDocumentModel = documentModel;
    }

    @NotNull
    @Override
    public Block getRootBlock() {
      return myRoot;
    }

    @NotNull
    @Override
    public FormattingDocumentModel getDocumentModel() {
      return myDocumentModel;
    }

    @Override
    public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
      return textRange;
    }

    @Override
    public TextRange shiftIndentInsideRange(ASTNode node, TextRange range, int indent) {
      return null;
    }

    @Override
    public void commitChanges() {

    }
  }
}
