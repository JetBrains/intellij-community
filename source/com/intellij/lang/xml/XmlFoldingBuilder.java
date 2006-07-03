package com.intellij.lang.xml;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
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
    XmlDocument xmlDocument = null;
    
    if (psiElement instanceof XmlFile) { 
      XmlFile file = ((XmlFile)psiElement);
      xmlDocument = file.getDocument();
    } else if (psiElement instanceof XmlDocument) {
      xmlDocument = (XmlDocument)psiElement;
    }
    
    XmlElement rootTag = xmlDocument == null ? null : xmlDocument.getRootTag();
    if (rootTag == null) {
      rootTag = xmlDocument;
    }
    List<FoldingDescriptor> foldings = null;

    if (rootTag != null) {
      foldings = new ArrayList<FoldingDescriptor>();

      if (rootTag instanceof XmlTag) {
        addElementsToFold(foldings, rootTag, document);

        // HTML tags in JSP
        for(PsiElement sibling = rootTag.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
          if (sibling instanceof XmlTag) addElementsToFold(foldings, (XmlElement) sibling, document);
        }
      }
      else doAddForChildren(xmlDocument, foldings, document);
    }

    return foldings != null ? foldings.toArray(new FoldingDescriptor[foldings.size()]):FoldingDescriptor.EMPTY;
  }

  protected void addElementsToFold(List<FoldingDescriptor> foldings, XmlElement tag, Document document) {
    if (addToFold(foldings, tag, document) ||
        tag instanceof JspXmlRootTag // has no name but has content
      ) {
      doAddForChildren(tag, foldings, document);
    }
  }

  private void doAddForChildren(final XmlElement tag, final List<FoldingDescriptor> foldings, final Document document) {
    final PsiElement[] children = tag.getChildren();

    for (PsiElement child : children) {
      ProgressManager.getInstance().checkCanceled();

      if (child instanceof XmlTag ||
          child instanceof XmlConditionalSection
         ) {
        addElementsToFold(foldings, (XmlElement)child, document);
      }
      else if(child instanceof XmlComment) {
        addToFold(foldings, (PsiElement)child, document);
      } else if (child instanceof XmlText) {
        final PsiElement[] grandChildren = child.getChildren();

        for(PsiElement grandChild:grandChildren) {
          ProgressManager.getInstance().checkCanceled();

          if (grandChild instanceof XmlComment) {
            addToFold(foldings, grandChild, document);
          }
        }
      } else {
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

  public TextRange getRangeToFold(PsiElement element) {
    if (element instanceof XmlTag) {
      final ASTNode tagNode = element.getNode();
      ASTNode tagNameElement = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagNode);
      if (tagNameElement == null) return null;

      int nameEnd = tagNameElement.getTextRange().getEndOffset();
      int end = tagNode.getLastChildNode().getTextRange().getStartOffset();
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
          return new TextRange(attributeEnd, end);
        }
      }

      return new TextRange(nameEnd, end);
    } else if (element instanceof XmlComment) {
      final XmlComment xmlComment = (XmlComment)element;
      final TextRange textRange = element.getTextRange();
      int commentStartOffset = getCommentStartOffset(xmlComment);
      int commentEndOffset = getCommentStartEnd(xmlComment);

      if (textRange.getEndOffset() - textRange.getStartOffset() > commentStartOffset + commentEndOffset) {
        return new TextRange(textRange.getStartOffset() + commentStartOffset, textRange.getEndOffset() - commentEndOffset);
      } else {
        return null;
      }
    } else if (element instanceof XmlConditionalSection) {
      final XmlConditionalSection conditionalSection = (XmlConditionalSection)element;
      final TextRange textRange = element.getTextRange();
      final PsiElement bodyStart = conditionalSection.getBodyStart();
      int startOffset = bodyStart != null ? bodyStart.getStartOffsetInParent() : 3;
      int endOffset = 3;

      if (textRange.getEndOffset() - textRange.getStartOffset() > startOffset + endOffset) {
        return new TextRange(textRange.getStartOffset() + startOffset, textRange.getEndOffset() - endOffset);
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  protected int getCommentStartOffset(final XmlComment element) {
    return 4;
  }
  
  protected int getCommentStartEnd(final XmlComment element) {
    return 3;
  }

  protected boolean addToFold(List<FoldingDescriptor> foldings, PsiElement elementToFold, Document document) {
    LOG.assertTrue(elementToFold.isValid());
    TextRange range = getRangeToFold(elementToFold);
    if (range == null) return false;
    
    if(range.getStartOffset() >= 0 && 
       range.getEndOffset() <= elementToFold.getContainingFile().getTextRange().getEndOffset()) {

      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      if (startLine < endLine) {
        foldings.add(new FoldingDescriptor(elementToFold.getNode(), range));
        return true;
      } 
    }
    
    return false;
  }

  public String getPlaceholderText(ASTNode node) {
    final PsiElement psi = node.getPsi();
    if (psi instanceof XmlTag ||
        psi instanceof XmlComment ||
        psi instanceof XmlConditionalSection
       ) return "...";
    return null;
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    final PsiElement psi = node.getPsi();
    if (psi instanceof XmlTag) {
      return CodeFoldingSettings.getInstance().isCollapseXmlTags();
    }
    return false;
  }
}
