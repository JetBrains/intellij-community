/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.codeFormatting.general;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.util.CharTable;

public class FormatterUtil {
  public static String getWhiteSpaceBefore(ASTNode element) {
    ASTNode wsCandidate = getWsCandidate(element);
    final StringBuffer result = new StringBuffer();
    while (wsCandidate != null && isSpaceTextElement(wsCandidate)) {
      result.append(wsCandidate.getText());
      final ASTNode newValue = getWsCandidate(wsCandidate);
      if (wsCandidate.getStartOffset() == newValue.getStartOffset()) break;
      wsCandidate = newValue;
    }
    return result.toString();
  }

  private static ASTNode getWsCandidate(ASTNode element) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      if (isSpaceTextElement(treePrev)) {
        return treePrev;
      }
      else if (treePrev.getTextLength() == 0) {
        return getWsCandidate(treePrev);
      }
      else {
        return element;
      }
    }
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return element;
    }
    else {
      return getWsCandidate(treeParent);
    }
  }

  private static ASTNode getLastChildOf(ASTNode element) {
    if (element == null) {
      return null;
    }
    if (element instanceof LeafElement) {
      return element;
    }
    else {
      ASTNode compositeElement = element;
      ChameleonTransforming.transformChildren(compositeElement);
      final ASTNode lastChild = compositeElement.getLastChildNode();
      if (lastChild == null) {
        return compositeElement;
      }
      else {
        return getLastChildOf(lastChild);
      }
    }
  }

  private static boolean isWhiteSpaceElement(ASTNode treePrev) {
    return treePrev.getElementType() == ElementType.WHITE_SPACE;
  }

  private static boolean isSpaceTextElement(ASTNode treePrev) {
    if (isWhiteSpaceElement(treePrev)) return true;
    final String text = treePrev.getText();
    return text.length() > 0 && text.trim().length() == 0;
  }

  public static String replaceWhiteSpace(final String whiteSpace,
                                         final ASTNode leafElement,
                                         final IElementType whiteSpaceToken) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(leafElement);

    ASTNode treePrev = findPreviousWhiteSpace(leafElement);
    if (treePrev == null) {
      treePrev = getWsCandidate(leafElement);
    }

    if (treePrev != null &&
        treePrev.getText().trim().length() == 0 &&
        treePrev.getElementType() != whiteSpaceToken &&
        treePrev.getTextLength() > 0 &&
        whiteSpace.length() >
                    0) {
      LeafElement whiteSpaceElement = Factory.createSingleLeafElement(treePrev.getElementType(), whiteSpace.toCharArray(), 0, whiteSpace.length(),
                                                          charTable, SharedImplUtil.getManagerByTree(leafElement));

      ASTNode treeParent = treePrev.getTreeParent();
      treeParent.replaceChild(treePrev, whiteSpaceElement);
    }
    else {
      LeafElement whiteSpaceElement = Factory.createSingleLeafElement(whiteSpaceToken, whiteSpace.toCharArray(), 0, whiteSpace.length(),
                                                                      charTable, SharedImplUtil.getManagerByTree(leafElement));

      if (treePrev == null) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(leafElement, whiteSpaceElement, leafElement, charTable);
        }
      }
      else if (!isSpaceTextElement(treePrev)) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(treePrev, whiteSpaceElement, leafElement, charTable);
        }
      }
      else if (!isWhiteSpaceElement(treePrev)) {
        return getWhiteSpaceBefore(leafElement);
      }
      else {
        final CompositeElement treeParent = (CompositeElement)treePrev.getTreeParent();
        if (whiteSpace.length() > 0) {
          treeParent.replaceChild(treePrev, whiteSpaceElement);
        }
        else {
          treeParent.removeChild(treePrev);
        }
        //treeParent.subtreeChanged();
      }

    }
    return getWhiteSpaceBefore(leafElement);
  }

  private static void addWhiteSpace(final ASTNode treePrev, final LeafElement whiteSpaceElement,
                                    ASTNode leafAfter, final CharTable charTable) {
    final ASTNode treeParent = treePrev.getTreeParent();
    if (treePrev.getTreePrev() != null && treePrev.getTreePrev().getElementType() == ElementType.XML_TEXT) {
      treePrev.getTreePrev().addChild(whiteSpaceElement);
    } else {
      if (isTag(treeParent) && leafAfter.getElementType() == ElementType.XML_START_TAG_START ||
          leafAfter.getElementType() == ElementType.XML_END_TAG_START) {
        CompositeElement xmlTextElement = Factory.createCompositeElement(ElementType.XML_TEXT, 
                                                                         charTable, 
                                                                         SharedImplUtil.getManagerByTree(treePrev));
        xmlTextElement.addChild(whiteSpaceElement);
        treeParent.addChild(xmlTextElement, treePrev);
        
      } else {
        treeParent.addChild(whiteSpaceElement, treePrev);
      }
    }
  }
  
  private static boolean isTag(final ASTNode treeParent) {
    return treeParent.getElementType() == ElementType.XML_TAG 
        || treeParent.getElementType() == ElementType.HTML_TAG;
  }

  private static ASTNode findPreviousWhiteSpace(final ASTNode leafElement) {
    final int offset = leafElement.getTextRange().getStartOffset() - 1;
    if (offset < 0) return null;
    final PsiElement found = SourceTreeToPsiMap.treeElementToPsi(leafElement).getContainingFile().findElementAt(offset);
    if (found == null) return null;
    final ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(found);
    if (treeElement.getElementType() == ElementType.WHITE_SPACE) return treeElement;
    return null;
  }

  public static ASTNode shiftTokenIndent(final Project project,
                                         final FileType fileType,
                                         final TreeElement leafElement,
                                         final int currentTokenPosShift) {
    return new Helper(fileType, project).shiftIndentInside(leafElement, currentTokenPosShift);
  }

  public static ASTNode getLeafNonSpaceBefore(final ASTNode element) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      ASTNode candidate = getLastChildOf(treePrev);
      if (candidate != null && !isSpaceTextElement(candidate) && candidate.getTextLength() > 0) {
        return candidate;
      }
      else {
        return getLeafNonSpaceBefore(candidate);
      }
    }
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return null;
    }
    else {
      return getLeafNonSpaceBefore(treeParent);
    }

  }

  public static ASTNode getElementBefore(final ASTNode element, final IElementType type) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      ASTNode candidate = getLastChildOf(treePrev);
      if (candidate != null && candidate.getElementType() == type) {
        return candidate;
      }
      else if (candidate != null && candidate.getTextLength() != 0) {
        return null;
      }
      else {
        return getElementBefore(candidate, type);
      }
    }
    else {
      final ASTNode treeParent = element.getTreeParent();

      if (treeParent == null || treeParent.getTreeParent() == null) {
        return null;
      }
      else {
        return getElementBefore(treeParent, type);
      }
    }
  }

  public static boolean isIncompleted(final ASTNode treeNode) {
    ASTNode lastChild = treeNode.getLastChildNode();
    while (lastChild != null && lastChild.getElementType() == ElementType.WHITE_SPACE) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) return false;
    if (lastChild.getElementType() == ElementType.ERROR_ELEMENT) return true;
    return isIncompleted(lastChild);
  }

  public static boolean isAfterIncompleted(final ASTNode child) {
    ASTNode current = child.getTreePrev();
    while (current != null) {
      if (current.getElementType() == ElementType.ERROR_ELEMENT) return true;
      if (current.getElementType() == ElementType.EMPTY_EXPRESSION) return true;
      if (current.getElementType() == ElementType.WHITE_SPACE || current.getTextLength() == 0) {
        current = current.getTreePrev();
      }
      else {
        return false;
      }
    }
    return false;
  }
}
