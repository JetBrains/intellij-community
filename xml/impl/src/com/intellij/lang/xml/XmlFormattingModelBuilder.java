/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public class XmlFormattingModelBuilder implements FormattingModelBuilder {

  @Override
  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    final ASTNode root = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
    final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(element.getContainingFile());
    return new XmlFormattingModel(element.getContainingFile(), createBlock(settings, root, documentModel), documentModel);
  }

  protected XmlBlock createBlock(CodeStyleSettings settings, ASTNode root, FormattingDocumentModelImpl documentModel) {
    return new XmlBlock(root, null, null, new XmlPolicy(settings, documentModel), null, null, false);
  }


}
