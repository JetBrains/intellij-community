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

import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;

public class FormatterUtil {

  public static String getWhiteSpaceBefore(TreeElement element, final IElementType fileElementType) {
    final TreeElement wsCandidate = getWsCandidate(element, fileElementType);
    if (wsCandidate == null || !isSpace(wsCandidate)) {
      return "";
    } else {
      return wsCandidate.getText();
    }
  }
  private static TreeElement getWsCandidate(TreeElement element, IElementType fileElementType) {
    if (element == null) return null;
    TreeElement treePrev = element.getTreePrev();
    if (treePrev != null) {
      TreeElement candidate = getLastChildOf(treePrev);
      if (candidate != null && isSpace(candidate)) {
        return candidate;
      }
      else if (candidate != null && candidate.getTextLength() == 0) {
        return getWsCandidate(candidate, fileElementType);
      }
      else {
        return element;
      }
    }
    final CompositeElement treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getElementType() == fileElementType) {
      return element;
    } else {
      return getWsCandidate(treeParent, fileElementType);
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

  private static boolean isSpace(TreeElement treePrev) {
    return treePrev.getElementType() == ElementType.WHITE_SPACE;
  }

  public static void replaceWhiteSpace(final String whiteSpace, final TreeElement leafElement, IElementType fileElementType) {
    LeafElement whiteSpaceElement = Factory.createSingleLeafElement(ElementType.WHITE_SPACE,
                                                                    whiteSpace.toCharArray(), 0, whiteSpace.length(),
                                                                    SharedImplUtil.findCharTableByTree(leafElement), null);

    TreeElement treePrev = getWsCandidate(leafElement, fileElementType);
    if (treePrev == null) {
      if (whiteSpace.length() > 0) {
        ChangeUtil.addChild(leafElement.getTreeParent(), whiteSpaceElement, leafElement);
      }
    } else if (!isSpace(treePrev)) {
      ChangeUtil.addChild(treePrev.getTreeParent(), whiteSpaceElement, treePrev);
    } else {
      ChangeUtil.replaceChild(treePrev.getTreeParent(), treePrev, whiteSpaceElement);
    }
  }

  public static TreeElement shiftTokenIndent(final Project project,
                                      final FileType fileType,
                                      final TreeElement leafElement,
                                      final int currentTokenPosShift) {
    return new Helper(fileType, project).shiftIndentInside(leafElement, currentTokenPosShift);
  }
}
