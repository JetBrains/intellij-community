/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.formatting.*;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class XsltFormattingModelBuilder implements CustomFormattingModelBuilder {
  private final FormattingModelBuilder myBuilder;

  public XsltFormattingModelBuilder(FormattingModelBuilder builder) {
    myBuilder = builder;
  }

  public boolean isEngagedToFormat(PsiElement context) {
    final PsiFile file = context.getContainingFile();
    if (file == null) {
      return false;
    } else if (file.getFileType() == XmlFileType.INSTANCE
            && file.getLanguage() == XMLLanguage.INSTANCE) {

      return XsltSupport.isXsltFile(file);
    }
    return false;
  }

  @Nullable
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return myBuilder.getRangeAffectingIndent(file, offset, elementAtOffset);
  }

  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    FormattingModel baseModel = myBuilder.createModel(element, settings);
    return new DelegatingFormattingModel(baseModel, getDelegatingBlock(settings, baseModel));
  }

  static Block getDelegatingBlock(final CodeStyleSettings settings, FormattingModel baseModel) {
    final Block block = baseModel.getRootBlock();
    if (block instanceof XmlBlock) {
      final XmlBlock xmlBlock = (XmlBlock)block;

      final XmlPolicy xmlPolicy = new XmlPolicy(settings, baseModel.getDocumentModel()) {
        @Override
        public boolean keepWhiteSpacesInsideTag(XmlTag xmlTag) {
          return super.keepWhiteSpacesInsideTag(xmlTag) || isXslTextTag(xmlTag);
        }

        @Override
        public boolean isTextElement(XmlTag tag) {
          return super.isTextElement(tag) || isXslTextTag(tag) || isXslValueOfTag(tag);
        }
      };

      final ASTNode node = xmlBlock.getNode();
      final Wrap wrap = xmlBlock.getWrap();
      final Alignment alignment = xmlBlock.getAlignment();
      final Indent indent = xmlBlock.getIndent();
      final TextRange textRange = xmlBlock.getTextRange();

      return new XmlBlock(node, wrap, alignment, xmlPolicy, indent, textRange);
    } else {
      return block;
    }
  }

  private static boolean isXslTextTag(XmlTag xmlTag) {
    return "text".equals(xmlTag.getLocalName()) && XsltSupport.XSLT_NS.equals(xmlTag.getNamespace());
  }

  private static boolean isXslValueOfTag(XmlTag xmlTag) {
    return "value-of".equals(xmlTag.getLocalName()) && XsltSupport.XSLT_NS.equals(xmlTag.getNamespace());
  }

}
