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

package com.intellij.lang;

import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class XmlCodeFoldingBuilder implements FoldingBuilder, DumbAware {
  private static final TokenSet XML_ATTRIBUTE_SET = TokenSet.create(XmlElementType.XML_ATTRIBUTE);
  private static final int MIN_TEXT_RANGE_LENGTH = 3;

  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    final PsiElement psiElement = node.getPsi();
    XmlDocument xmlDocument = null;

    if (psiElement instanceof XmlFile) {
      XmlFile file = (XmlFile)psiElement;
      xmlDocument = file.getDocument();
    }
    else if (psiElement instanceof XmlDocument) {
      xmlDocument = (XmlDocument)psiElement;
    }

    XmlElement rootTag = xmlDocument == null ? null : xmlDocument.getRootTag();
    if (rootTag == null) {
      rootTag = xmlDocument;
    }
    List<FoldingDescriptor> foldings = null;

    if (rootTag != null) {
      foldings = new ArrayList<>();

      doAddForChildren(xmlDocument, foldings, document);
    }

    return foldings != null ? foldings.toArray(new FoldingDescriptor[foldings.size()]):FoldingDescriptor.EMPTY;
  }

  protected void addElementsToFold(List<FoldingDescriptor> foldings, XmlElement tag, Document document) {
    addToFold(foldings, tag, document);
    doAddForChildren(tag, foldings, document);
  }

  protected void doAddForChildren(final XmlElement tag, final List<FoldingDescriptor> foldings, final Document document) {
    final PsiElement[] children = tag.getChildren();

    for (PsiElement child : children) {
      ProgressManager.checkCanceled();

      if (child instanceof XmlTag || child instanceof XmlConditionalSection) {
        addElementsToFold(foldings, (XmlElement)child, document);
      }
      else if (child instanceof XmlComment) {
        addToFold(foldings, child, document);
      }
      else if (child instanceof XmlText || child instanceof XmlProlog) {
        final PsiElement[] grandChildren = child.getChildren();

        for (PsiElement grandChild : grandChildren) {
          ProgressManager.checkCanceled();

          if (grandChild instanceof XmlComment || isEntity(grandChild)) {
            addToFold(foldings, grandChild, document);
          }
        }
      }
      else if (isEntity(child) || child instanceof XmlAttribute && isAttributeShouldBeFolded((XmlAttribute)child)) {
        addToFold(foldings, child, document);
      }
      else {
        final Language language = child.getLanguage();
        if (!(language instanceof XMLLanguage) && language != Language.ANY) {
          final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);

          if (foldingBuilder != null) {
            final FoldingDescriptor[] foldingDescriptors = foldingBuilder.buildFoldRegions(child.getNode(), document);

            ContainerUtil.addAll(foldings, foldingDescriptors);
          }
        }
      }
    }
  }

  @Nullable
  public TextRange getRangeToFold(PsiElement element) {
    if (element instanceof XmlTag) {
      final ASTNode tagNode = element.getNode();
      XmlToken tagNameElement = XmlTagUtil.getStartTagNameElement((XmlTag)element);
      if (tagNameElement == null) return null;

      int nameEnd = tagNameElement.getTextRange().getEndOffset();
      int end = tagNode.getLastChildNode().getTextRange().getEndOffset() - 1;  // last child node can be another tag in unbalanced tree
      ASTNode[] attributes = tagNode.getChildren(XML_ATTRIBUTE_SET);

      if (attributes.length > 0) {
        ASTNode lastAttribute = attributes[attributes.length - 1];
        ASTNode lastAttributeBeforeCR = null;

        for (ASTNode child = tagNode.getFirstChildNode(); child != lastAttribute.getTreeNext(); child = child.getTreeNext()) {
          if (child.getElementType() == XmlElementType.XML_ATTRIBUTE) {
            lastAttributeBeforeCR = child;
          } else if (child.getPsi() instanceof PsiWhiteSpace) {
            if (child.textContains('\n')) break;
          }
        }

        if (lastAttributeBeforeCR != null) {
          int attributeEnd = lastAttributeBeforeCR.getTextRange().getEndOffset();
          return new UnfairTextRange(attributeEnd, end);
        }
      }

      return new UnfairTextRange(nameEnd, end);
    } else if (element instanceof XmlComment) {
      final XmlComment xmlComment = (XmlComment)element;
      final TextRange textRange = element.getTextRange();
      int commentStartOffset = getCommentStartOffset(xmlComment);
      int commentEndOffset = getCommentStartEnd(xmlComment);

      if (textRange.getEndOffset() - textRange.getStartOffset() > commentStartOffset + commentEndOffset) {
        return new TextRange(textRange.getStartOffset() + commentStartOffset, textRange.getEndOffset() - commentEndOffset);
      }
      else {
        return null;
      }
    }
    else if (element instanceof XmlConditionalSection) {
      final XmlConditionalSection conditionalSection = (XmlConditionalSection)element;
      final TextRange textRange = element.getTextRange();
      final PsiElement bodyStart = conditionalSection.getBodyStart();
      int startOffset = bodyStart != null ? bodyStart.getStartOffsetInParent() : MIN_TEXT_RANGE_LENGTH;
      int endOffset = MIN_TEXT_RANGE_LENGTH;

      if (textRange.getEndOffset() - textRange.getStartOffset() > startOffset + endOffset) {
        return new TextRange(textRange.getStartOffset() + startOffset, textRange.getEndOffset() - endOffset);
      }
      else {
        return null;
      }
    }
    else if (element instanceof XmlAttribute) {
      final XmlAttributeValue valueElement = ((XmlAttribute)element).getValueElement();
      return valueElement != null ? valueElement.getValueTextRange() : null;
    }
    else if (isEntity(element)) {
      return element.getTextRange();
    }
    else {
      return null;
    }
  }

  protected int getCommentStartOffset(final XmlComment element) {
    return 4;
  }

  protected int getCommentStartEnd(final XmlComment element) {
    return MIN_TEXT_RANGE_LENGTH;
  }

  protected boolean addToFold(List<FoldingDescriptor> foldings, PsiElement elementToFold, Document document) {
    PsiUtilCore.ensureValid(elementToFold);
    TextRange range = getRangeToFold(elementToFold);
    if (range == null) return false;

    if(range.getStartOffset() >= 0 &&
       range.getEndOffset() <= elementToFold.getContainingFile().getTextRange().getEndOffset() &&
       range.getEndOffset() <= document.getTextLength() // psi and document maybe not in sync after error
      ) {

      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      final boolean entity = isEntity(elementToFold);
      if (startLine < endLine || elementToFold instanceof XmlAttribute || entity) {
        if (range.getStartOffset() + MIN_TEXT_RANGE_LENGTH < range.getEndOffset() || entity) {
          foldings.add(new FoldingDescriptor(elementToFold.getNode(), range));
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final PsiElement psi = node.getPsi();
    if (psi instanceof XmlAttribute && "src".equalsIgnoreCase(((XmlAttribute)psi).getName())) {
      return "data:";
    }
    if (psi instanceof XmlTag ||
        psi instanceof XmlComment ||
        psi instanceof XmlAttribute ||
        psi instanceof XmlConditionalSection
      ) return "...";
    if (isEntity(psi)) {
      final XmlEntityDecl resolve = XmlEntityRefImpl.resolveEntity((XmlElement)psi, psi.getText(), psi.getContainingFile());
      final XmlAttributeValue value = resolve != null ? resolve.getValueElement() : null;
      if (value != null) {
        return getEntityValue(value);
      }
    }
    return null;
  }

  private static String getEntityValue(XmlAttributeValue value) {
    final String result = value.getValue();
    final int i = result.indexOf('#');
    if (i > 0) {
      final int charNum = StringUtil.parseInt(StringUtil.trimEnd(result.substring(i + 1), ";"), -1);
      return charNum >= 0 ? String.valueOf((char)charNum) : null;
    }
    return null;
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    final PsiElement psi = node.getPsi();
    final XmlCodeFoldingSettings foldingSettings = getFoldingSettings();
    return (psi instanceof XmlTag && foldingSettings.isCollapseXmlTags())
           || (psi instanceof XmlAttribute && (foldStyle((XmlAttribute)psi, foldingSettings) || foldSrc((XmlAttribute)psi, foldingSettings)))
           || isEntity(psi) && foldingSettings.isCollapseEntities();
  }

  private static boolean foldSrc(XmlAttribute psi, XmlCodeFoldingSettings settings) {
    return settings.isCollapseDataUri() && "src".equals(psi.getName());
  }

  private static boolean foldStyle(XmlAttribute psi, XmlCodeFoldingSettings settings) {
    return settings.isCollapseHtmlStyleAttribute() && HtmlUtil.STYLE_ATTRIBUTE_NAME.equalsIgnoreCase(psi.getName());
  }

  protected boolean isEntity(PsiElement psi) {
    return psi instanceof XmlEntityRef ||
           (psi instanceof XmlTokenImpl && ((XmlTokenImpl)psi).getElementType() == XmlTokenType.XML_CHAR_ENTITY_REF);
  }

  private static boolean isAttributeShouldBeFolded(XmlAttribute child) {
    return HtmlUtil.isHtmlFile(child.getContainingFile()) &&
           (HtmlUtil.STYLE_ATTRIBUTE_NAME.equalsIgnoreCase(child.getName()) ||
            "src".equals(child.getName()) && child.getValue() != null && URLUtil.isDataUri(child.getValue()));
  }

  protected abstract XmlCodeFoldingSettings getFoldingSettings();
}
