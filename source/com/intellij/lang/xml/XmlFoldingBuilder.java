package com.intellij.lang.xml;

import com.intellij.codeInsight.folding.impl.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.xml.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: May 4, 2005
 * Time: 3:23:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlFoldingBuilder implements FoldingBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.xml.XmlFoldingBuilder");
  private static TokenSet XML_ATTRIBUTE_SET = TokenSet.create(XmlElementType.XML_ATTRIBUTE);

  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    final PsiElement psiElement = node.getPsi();
    if (!(psiElement instanceof XmlFile)) return FoldingDescriptor.EMPTY;
    XmlFile file = ((XmlFile)psiElement);
    final XmlDocument xmlDocument = file.getDocument();
    XmlTag rootTag = xmlDocument == null ? null : xmlDocument.getRootTag();
    List<FoldingDescriptor> foldings = null;

    if (rootTag != null) {
      foldings = new ArrayList<FoldingDescriptor>();
      addElementsToFold(foldings, rootTag, document);
    }

    return foldings != null ? foldings.toArray(new FoldingDescriptor[foldings.size()]):FoldingDescriptor.EMPTY;
  }

  protected void addElementsToFold(List<FoldingDescriptor> foldings, XmlTag tag, Document document) {
    if (addToFold(foldings, tag, document) ||
        tag instanceof JspXmlRootTag // has no name but has content
      ) {
      PsiElement[] children = tag.getChildren();
      for (PsiElement child : children) {
        if (child instanceof XmlTag) {
          ProgressManager.getInstance().checkCanceled();
          addElementsToFold(foldings, (XmlTag)child, document);
        }
        else {
          final Language language = child.getLanguage();
          if (!(language instanceof XMLLanguage) && language != Language.ANY) {
            final FoldingBuilder foldingBuilder = language.getFoldingBuilder();

            if (foldingBuilder != null) {
              final FoldingDescriptor[] foldingDescriptors = foldingBuilder.buildFoldRegions(child.getNode(), document);

              if (foldingDescriptors != null) {
                for (FoldingDescriptor descriptor : foldingDescriptors) {
                  foldings.add(descriptor);
                }
              }
            }
          }
        }
      }
    }
  }

  public static TextRange getRangeToFold(PsiElement element) {
    if (element instanceof XmlTag) {
      final ASTNode tagNode = element.getNode();
      ASTNode tagNameElement = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagNode);
      if (tagNameElement == null) return null;

      int nameEnd = tagNameElement.getTextRange().getEndOffset();
      int end = tagNode.getLastChildNode().getTextRange().getStartOffset();
      ASTNode[] attributes = tagNode.findChildrenByFilter(XML_ATTRIBUTE_SET);

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
          return new TextRange(attributeEnd, end);
        }
      }

      return new TextRange(nameEnd, end);
    } else {
      return null;
    }
  }

  protected boolean addToFold(List<FoldingDescriptor> foldings, PsiElement elementToFold, Document document) {
    LOG.assertTrue(elementToFold.isValid());
    TextRange range = getRangeToFold(elementToFold);
    if (range == null) return false;
    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= elementToFold.getContainingFile().getTextRange().getEndOffset());

    int startLine = document.getLineNumber(range.getStartOffset());
    int endLine = document.getLineNumber(range.getEndOffset() - 1);
    if (startLine < endLine) {
      foldings.add(new FoldingDescriptor(elementToFold.getNode(), range));
      return true;
    } else {
      return false;
    }
  }

  public String getPlaceholderText(ASTNode node) {
    if (node.getPsi() instanceof XmlTag) return "...";
    return null;
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    if (node.getPsi() instanceof XmlTag) {
      return CodeFoldingSettings.getInstance().COLLAPSE_XML_TAGS;
    }
    return false;
  }
}
