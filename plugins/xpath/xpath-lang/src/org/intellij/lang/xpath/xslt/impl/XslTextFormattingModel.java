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

import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.xml.XmlTag;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

class XslTextFormattingModel implements FormattingModel {
    private final FormattingModel myModel;
    private final Block myRootBlock;

    public XslTextFormattingModel(FormattingModel model, CodeStyleSettings settings) {
        myModel = model;

        final Block block = myModel.getRootBlock();
        if (block instanceof XmlBlock) {
            final XmlBlock xmlBlock = (XmlBlock)block;

            final XmlPolicy xmlPolicy = new XmlPolicy(settings, getDocumentModel()) {
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

            myRootBlock = new XmlBlock(node, wrap, alignment, xmlPolicy, indent, textRange);
        } else {
            myRootBlock = block;
        }
    }

    private static boolean isXslTextTag(XmlTag xmlTag) {
        return "text".equals(xmlTag.getLocalName()) && XsltSupport.XSLT_NS.equals(xmlTag.getNamespace());
    }

    private static boolean isXslValueOfTag(XmlTag xmlTag) {
        return "value-of".equals(xmlTag.getLocalName()) && XsltSupport.XSLT_NS.equals(xmlTag.getNamespace());
    }

    @NotNull
    public Block getRootBlock() {
        return myRootBlock;
    }

    @NotNull
    public FormattingDocumentModel getDocumentModel() {
        return myModel.getDocumentModel();
    }

    public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
        return myModel.replaceWhiteSpace(textRange, whiteSpace);
    }

    public TextRange shiftIndentInsideRange(TextRange range, int indent) {
        return myModel.shiftIndentInsideRange(range, indent);
    }

    public void commitChanges() {
        myModel.commitChanges();
    }
}
