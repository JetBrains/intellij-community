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
import com.intellij.psi.tree.IElementType;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.PomModel;
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
      ASTNode candidate = getLastChildOf(treePrev);
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
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return element;
    } else {
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
                                         final IElementType whiteSpaceToken,
                                         PomModelEvent event) {
    final TreeChangeEvent changeSet = event == null ? null : (TreeChangeEvent)event.getChangeSet(((PomModel)event.getSource()).getModelAspect(TreeAspect.class));

    final CharTable charTable = SharedImplUtil.findCharTableByTree(leafElement);
    LeafElement whiteSpaceElement = Factory.createSingleLeafElement(whiteSpaceToken,
                                                                    whiteSpace.toCharArray(), 0, whiteSpace.length(),
                                                                    charTable, null);

    ASTNode treePrev = getWsCandidate(leafElement);
    if (treePrev == null) {
      if (whiteSpace.length() > 0) {
        if(changeSet == null){
          final ASTNode treeParent = leafElement.getTreeParent();
          treeParent.addChild(whiteSpaceElement, leafElement);
        }
        else {
          TreeUtil.insertBefore((TreeElement)leafElement, whiteSpaceElement);
          ((CompositeElement)leafElement.getTreeParent()).subtreeChanged();
          changeSet.addElementaryChange(whiteSpaceElement, ChangeInfoImpl.create(ChangeInfo.ADD, whiteSpaceElement, charTable));
        }
      }
    } else if (!isSpaceTextElement(treePrev)) {
      if (changeSet != null) {
        TreeUtil.insertBefore((TreeElement)treePrev, whiteSpaceElement);
        ((CompositeElement)treePrev.getTreeParent()).subtreeChanged();
        changeSet.addElementaryChange(whiteSpaceElement, ChangeInfoImpl.create(ChangeInfo.ADD, whiteSpaceElement, charTable));
      }
      else{
        final ASTNode treeParent = treePrev.getTreeParent();
        treeParent.addChild(whiteSpaceElement, treePrev);
      }
    } else if (!isWhiteSpaceElement(treePrev)){
      return getWhiteSpaceBefore(leafElement);
    } else {
      if (changeSet != null) {
        TreeUtil.replaceWithList((TreeElement)treePrev, whiteSpaceElement);
        final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, whiteSpaceElement, charTable);
        change.setReplaced(treePrev);
        whiteSpaceElement.getTreeParent().subtreeChanged();
        changeSet.addElementaryChange(whiteSpaceElement, change);
      }
      else{
        final CompositeElement treeParent = (CompositeElement)treePrev.getTreeParent();
        treeParent.replaceChild(treePrev, whiteSpaceElement);
        treeParent.subtreeChanged();
      }
    }

    return getWhiteSpaceBefore(leafElement);
  }

  public static ASTNode shiftTokenIndent(final Project project,
                                      final FileType fileType,
                                      final TreeElement leafElement,
                                      final int currentTokenPosShift) {
    return new Helper(fileType, project).shiftIndentInside(leafElement, currentTokenPosShift);
  }

  public static ASTNode getNonLeafSpaceBefore(final ASTNode element) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      ASTNode candidate = getLastChildOf(treePrev);
      if (candidate != null && !isSpaceTextElement(candidate)) {
        return candidate;
      }
      else {
        return getNonLeafSpaceBefore(candidate);
      }
    }
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return null;
    } else {
      return getNonLeafSpaceBefore(treeParent);
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
    } else {
      final ASTNode treeParent = element.getTreeParent();

      if (treeParent == null || treeParent.getTreeParent() == null) {
        return null;
      } else {
        return getElementBefore(treeParent, type);
      }
    }
  }
}
