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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;

public class FormatterUtil {

  public static String getWhiteSpaceBefore(TreeElement element) {
    TreeElement wsCandidate = getWsCandidate(element);
    final StringBuffer result = new StringBuffer();
    while (wsCandidate != null && isSpaceTextElement(wsCandidate)) {
      result.append(wsCandidate.getText());
      final TreeElement newValue = getWsCandidate(wsCandidate);
      if (wsCandidate == newValue) break;
      wsCandidate = newValue;
    }
    return result.toString();
  }
  private static TreeElement getWsCandidate(TreeElement element) {
    if (element == null) return null;
    TreeElement treePrev = element.getTreePrev();
    if (treePrev != null) {
      TreeElement candidate = getLastChildOf(treePrev);
      if (candidate != null && isSpaceTextElement(candidate)) {
        return candidate;
      }
      else if (candidate != null && candidate.getTextLength() == 0) {
        return getWsCandidate(candidate);
      }
      else {
        return element;
      }
    }
    final CompositeElement treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return element;
    } else {
      return getWsCandidate(treeParent);
    }
  }

  private static TreeElement getLastChildOf(TreeElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof LeafElement) {
      return (LeafElement)element;
    }
    else {
      CompositeElement compositeElement = ((CompositeElement)element);
      ChameleonTransforming.transformChildren(compositeElement);
      final TreeElement lastChild = compositeElement.lastChild;
      if (lastChild == null) {
        return compositeElement;
      }
      else {
        return getLastChildOf(lastChild);
      }
    }
  }

  private static boolean isWhiteSpaceElement(TreeElement treePrev) {
    return treePrev.getElementType() == ElementType.WHITE_SPACE;
  }

  private static boolean isSpaceTextElement(TreeElement treePrev) {
    if (isWhiteSpaceElement(treePrev)) return true;
    final String text = treePrev.getText();
    return text.length() > 0 && text.trim().length() == 0;
  }

  public static String replaceWhiteSpace(final String whiteSpace, final TreeElement leafElement) {
    LeafElement whiteSpaceElement = Factory.createSingleLeafElement(ElementType.WHITE_SPACE,
                                                                    whiteSpace.toCharArray(), 0, whiteSpace.length(),
                                                                    SharedImplUtil.findCharTableByTree(leafElement), null);

    TreeElement treePrev = getWsCandidate(leafElement);
    if (treePrev == null) {
      if (whiteSpace.length() > 0) {
        ChangeUtil.addChild(leafElement.getTreeParent(), whiteSpaceElement, leafElement);
      }
    } else if (!isSpaceTextElement(treePrev)) {
      ChangeUtil.addChild(treePrev.getTreeParent(), whiteSpaceElement, treePrev);
    } else if (!isWhiteSpaceElement(treePrev)){
      return getWhiteSpaceBefore(leafElement);
    } else {
      ChangeUtil.replaceChild(treePrev.getTreeParent(), treePrev, whiteSpaceElement);
    }

    return getWhiteSpaceBefore(leafElement);
  }

  public static TreeElement shiftTokenIndent(final Project project,
                                      final FileType fileType,
                                      final TreeElement leafElement,
                                      final int currentTokenPosShift) {
    return new Helper(fileType, project).shiftIndentInside(leafElement, currentTokenPosShift);
  }
}
