// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang;

import com.intellij.lang.folding.*;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.html.HtmlEmbeddedContentImpl;
import com.intellij.psi.impl.source.resolve.impl.XmlEntityRefUtil;
import com.intellij.psi.impl.source.xml.XmlEntityRefImpl;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class XmlCodeFoldingBuilder extends CustomFoldingBuilder implements DumbAware {
  private static final TokenSet XML_ATTRIBUTE_SET = TokenSet.forAllMatching(el -> el instanceof IXmlAttributeElementType);
  private static final int MIN_TEXT_RANGE_LENGTH = 3;

  @Override
  public void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> foldings,
                                       @NotNull PsiElement psiElement,
                                       @NotNull Document document,
                                       boolean quick) {
    XmlDocument xmlDocument;

    if (psiElement instanceof XmlFile file) {
      xmlDocument = file.getDocument();
    }
    else if (psiElement instanceof XmlDocument) {
      xmlDocument = (XmlDocument)psiElement;
    } else {
      // handle embedded templates
      xmlDocument = PsiTreeUtil.getChildOfType(psiElement, XmlDocument.class);
    }

    XmlElement rootTag = xmlDocument == null ? null : xmlDocument.getRootTag();
    if (rootTag == null) {
      rootTag = xmlDocument;
    }

    if (rootTag != null) {
      doAddForChildren(xmlDocument, foldings, document);
    }
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
        if (!(language instanceof XMLLanguage) && language != Language.ANY ||
            child instanceof HtmlEmbeddedContentImpl) {
          final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(language);

          if (foldingBuilder != null) {
            final FoldingDescriptor[] foldingDescriptors = foldingBuilder.buildFoldRegions(child.getNode(), document);

            ContainerUtil.addAll(foldings, foldingDescriptors);
          }
        }
      }
    }
  }

  public @Nullable TextRange getRangeToFold(PsiElement element) {
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
    }
    if (element instanceof XmlComment xmlComment) {
      final TextRange textRange = element.getTextRange();
      int commentStartOffset = getCommentStartOffset(xmlComment);
      int commentEndOffset = getCommentStartEnd(xmlComment);

      if (textRange.getEndOffset() - textRange.getStartOffset() > commentStartOffset + commentEndOffset) {
        return new TextRange(textRange.getStartOffset() + commentStartOffset, textRange.getEndOffset() - commentEndOffset);
      }
      return null;
    }
    if (element instanceof XmlConditionalSection conditionalSection) {
      final TextRange textRange = element.getTextRange();
      final PsiElement bodyStart = conditionalSection.getBodyStart();
      int startOffset = bodyStart != null ? bodyStart.getStartOffsetInParent() : MIN_TEXT_RANGE_LENGTH;
      int endOffset = MIN_TEXT_RANGE_LENGTH;

      if (textRange.getEndOffset() - textRange.getStartOffset() > startOffset + endOffset) {
        return new TextRange(textRange.getStartOffset() + startOffset, textRange.getEndOffset() - endOffset);
      }
      return null;
    }
    if (element instanceof XmlAttribute) {
      final XmlAttributeValue valueElement = ((XmlAttribute)element).getValueElement();
      return valueElement != null ? valueElement.getValueTextRange() : null;
    }
    if (isEntity(element)) {
      return element.getTextRange();
    }
    return null;
  }

  protected int getCommentStartOffset(final XmlComment element) {
    return 4;
  }

  protected int getCommentStartEnd(final XmlComment element) {
    return MIN_TEXT_RANGE_LENGTH;
  }

  protected boolean addToFold(List<? super FoldingDescriptor> foldings, PsiElement elementToFold, Document document) {
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
          ASTNode node = elementToFold.getNode();
          String placeholder = getLanguagePlaceholderText(node, range);
          foldings.add(placeholder != null ? new FoldingDescriptor(node, range, null, placeholder) : new FoldingDescriptor(node, range));
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    final PsiElement psi = node.getPsi();
    FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psi.getLanguage());
    if (foldingBuilder == this || foldingBuilder instanceof CompositeFoldingBuilder) {
      if (psi instanceof XmlAttribute && "src".equalsIgnoreCase(((XmlAttribute)psi).getName())) {
        return "data:";
      }
      if (psi instanceof XmlTag ||
          psi instanceof XmlComment ||
          psi instanceof XmlAttribute ||
          psi instanceof XmlConditionalSection) {
        return "...";
      }
      if (isEntity(psi)) {
        final String value = getEntityPlaceholder(psi);
        if (value != null) return value;
      }
      return null;
    }

    // can't call CustomFoldingBuilder.getLanguagePlaceholderText() directly but this CustomFoldingBuilder.getPlaceholderText() will do just fine
    return foldingBuilder instanceof CustomFoldingBuilder ? ((CustomFoldingBuilder)foldingBuilder).getPlaceholderText(node, range) : null;
  }

  private static @Nullable String getEntityPlaceholder(@NotNull PsiElement psi) {
    try {
      String text = psi.getText();
      String fastPath = StringUtil.unescapeXmlEntities(text);
      if (!StringUtil.equals(fastPath, text)) return fastPath;
      if (psi.isValid()) {
        final XmlEntityDecl resolve = XmlEntityRefUtil.resolveEntity((XmlElement)psi, text, psi.getContainingFile());
        final XmlAttributeValue value = resolve != null ? resolve.getValueElement() : null;
        if (value != null) {
          return getEntityValue(value.getValue());
        }
      }
    } catch (IndexNotReadyException ignore) {}
    return null;
  }

  public static @Nullable String getEntityValue(@Nullable String value) {
    int i = value != null ? value.indexOf('#') : -1;
    if (i > 0) {
      int radix = 10;
      String number = value.substring(i + 1);
      if (StringUtil.startsWithIgnoreCase(number, "x")) {
        radix = 16;
        number = number.substring(1);
      }
      try {
        final int charNum = Integer.parseInt(StringUtil.trimEnd(number, ";"), radix);
        return String.valueOf((char)charNum);
      } catch (Exception ignored) {}
    }
    return null;
  }

  @Override
  public boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    final PsiElement psi = node.getPsi();
    FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psi.getLanguage());

    if (foldingBuilder == this || foldingBuilder instanceof CompositeFoldingBuilder) {
      return isPsiElementCollapsedByDefault(psi);
    }
    return foldingBuilder.isCollapsedByDefault(node);
  }

  private boolean isPsiElementCollapsedByDefault(PsiElement psi) {
    final XmlCodeFoldingSettings foldingSettings = getFoldingSettings();
    return psi instanceof XmlTag && foldingSettings.isCollapseXmlTags() ||
           psi instanceof XmlAttribute && (foldStyle((XmlAttribute)psi, foldingSettings) || foldSrc((XmlAttribute)psi, foldingSettings)) ||
           isEntity(psi) && foldingSettings.isCollapseEntities() && hasEntityPlaceholder(psi);
  }

  @Override
  public boolean isRegionCollapsedByDefault(@NotNull FoldingDescriptor foldingDescriptor) {
    final PsiElement psi = foldingDescriptor.getElement().getPsi();
    FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psi.getLanguage());

    if (foldingBuilder == this || foldingBuilder instanceof CompositeFoldingBuilder) {
      return isPsiElementCollapsedByDefault(psi);
    }
    return foldingBuilder.isCollapsedByDefault(foldingDescriptor);
  }

  private static boolean hasEntityPlaceholder(PsiElement psi) {
    return getEntityPlaceholder(psi) != null;
  }

  private static boolean foldSrc(XmlAttribute psi, XmlCodeFoldingSettings settings) {
    return settings.isCollapseDataUri() && "src".equals(psi.getName());
  }

  private static boolean foldStyle(XmlAttribute psi, XmlCodeFoldingSettings settings) {
    return settings.isCollapseHtmlStyleAttribute() && HtmlUtil.STYLE_ATTRIBUTE_NAME.equalsIgnoreCase(psi.getName());
  }

  private static boolean isEntity(PsiElement psi) {
    return psi instanceof XmlEntityRef ||
           psi instanceof XmlTokenImpl && ((XmlTokenImpl)psi).getElementType() == XmlTokenType.XML_CHAR_ENTITY_REF;
  }

  private static boolean isAttributeShouldBeFolded(XmlAttribute child) {
    return HtmlUtil.isHtmlFile(child.getContainingFile()) &&
           (HtmlUtil.STYLE_ATTRIBUTE_NAME.equalsIgnoreCase(child.getName()) ||
            "src".equals(child.getName()) && child.getValue() != null && URLUtil.isDataUri(child.getValue()));
  }

  protected abstract XmlCodeFoldingSettings getFoldingSettings();

  @Override
  protected boolean isCustomFoldingRoot(@NotNull ASTNode node) {
    return node.getElementType() == XmlElementType.XML_TAG;
  }

  @Override
  protected boolean isCustomFoldingCandidate(@NotNull ASTNode node) {
    return node.getElementType() == XmlTokenType.XML_COMMENT_CHARACTERS;
  }
}
