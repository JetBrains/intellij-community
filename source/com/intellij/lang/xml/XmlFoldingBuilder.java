package com.intellij.lang.xml;

import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.codeInsight.folding.impl.CodeFoldingSettings;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: May 4, 2005
 * Time: 3:23:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlFoldingBuilder implements FoldingBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.xml.XmlFoldingBuilder");

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
    if (addToFold(foldings, tag, document)) {
      PsiElement[] children = tag.getChildren();
      for (int i = 0; i < children.length; i++) {
        if (children[i] instanceof XmlTag) {
          ProgressManager.getInstance().checkCanceled();
          addElementsToFold(foldings, (XmlTag)children[i], document);
        } else {
          final Language language = children[i].getLanguage();
          if (!(language instanceof XMLLanguage) && language != Language.ANY) {
            final FoldingBuilder foldingBuilder = language.getFoldingBuilder();

            if (foldingBuilder!=null) {
              final FoldingDescriptor[] foldingDescriptors = foldingBuilder.buildFoldRegions(children[i].getNode(), document);

              if (foldingDescriptors!=null) {
                for (int j = 0; j < foldingDescriptors.length; j++) {
                  foldings.add(foldingDescriptors[j]);
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
      XmlTag tag = (XmlTag) element;
      ASTNode tagNameElement = XmlChildRole.START_TAG_NAME_FINDER.findChild(SourceTreeToPsiMap.psiElementToTree(tag));
      if (tagNameElement == null) return null;

      int nameEnd = tagNameElement.getTextRange().getEndOffset();
      int end = tag.getLastChild().getTextRange().getStartOffset();
      XmlAttribute[] attributes = tag.getAttributes();

      if (attributes.length > 0) {
        XmlAttribute lastAttribute = attributes[attributes.length - 1];
        XmlAttribute lastAttributeBeforeCR = null;

        for (PsiElement child = tag.getFirstChild(); child != lastAttribute.getNextSibling(); child = child.getNextSibling()) {
          if (child instanceof XmlAttribute) {
            lastAttributeBeforeCR = (XmlAttribute) child;
          } else if (child instanceof PsiWhiteSpace) {
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
