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
package com.intellij.lang.xhtml;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.xml.HtmlPolicy;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.lang.xml.XmlFormattingModel;
import org.jetbrains.annotations.NotNull;

public class XhtmlFormattingModelBuilder implements FormattingModelBuilder {
  @Override
  @NotNull
    public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    final PsiFile psiFile = element.getContainingFile();
    final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(psiFile);
    return new XmlFormattingModel(psiFile,
                                                            new XmlBlock(SourceTreeToPsiMap.psiElementToTree(psiFile),
                                                                         null, null,
                                                                         new HtmlPolicy(settings, documentModel), null, null, false),
                                                            documentModel);
  }

}