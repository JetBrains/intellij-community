package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;

public class ClassElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ClassElement");

  public ClassElement(IElementType type) {
    super(type);
  }

  public int getTextOffset() {
    TreeElement name = findChildByRole(ChildRole.NAME);
    if (name != null) {
      return name.getTextOffset();
    }
    else {
      return super.getTextOffset();
    }
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);

    PsiClass psiClass = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(this);
    if (anchor == null) {
      if (before == null) {
        if (first == last) {
          PsiElement firstPsi = SourceTreeToPsiMap.treeElementToPsi(first);
          PsiElement psiElement = firstPsi instanceof PsiMember ? CodeEditUtil.getDefaultAnchor(psiClass, (PsiMember)firstPsi) : null;
          anchor = psiElement != null ? SourceTreeToPsiMap.psiElementToTree(psiElement) : null;
        }
        else {
          anchor = findChildByRole(ChildRole.RBRACE);
        }
        before = Boolean.TRUE;
      }
      else if (!before.booleanValue()) {
        anchor = findChildByRole(ChildRole.LBRACE);
      }
      else {
        anchor = findChildByRole(ChildRole.RBRACE);
      }
    }

    if (isEnum()) {
      if (!ENUM_CONSTANT_LIST_ELEMENTS_BIT_SET.isInSet(first.getElementType())) {
        TreeElement semicolonPlace = findEnumConstantListDelimiterPlace();
        if (semicolonPlace.getElementType() != SEMICOLON) {
            final LeafElement semicolon = Factory.createSingleLeafElement(SEMICOLON, new char[]{';'}, 0, 1,
                                                                          SharedImplUtil.findCharTableByTree(this), getManager());
            addInternal(semicolon, semicolon, semicolonPlace, Boolean.FALSE);
            semicolonPlace = semicolon;
        }
        for (TreeElement run = anchor; run != null; run = run.getTreeNext()) {
          if (run == semicolonPlace) {
            anchor = before.booleanValue() ? semicolonPlace.getTreeNext() : semicolonPlace;
            break;
          }
        }
      }
    }

    TreeElement afterLast = last.getTreeNext();
    TreeElement next;
    for (TreeElement child = first; child != afterLast; child = next) {
      next = child.getTreeNext();
      if (child.getElementType() == ElementType.METHOD && ((PsiMethod)SourceTreeToPsiMap.treeElementToPsi(child)).isConstructor()) {
        TreeElement oldIdentifier = ((CompositeElement)child).findChildByRole(ChildRole.NAME);
        TreeElement newIdentifier = (TreeElement)findChildByRole(ChildRole.NAME).clone();
        newIdentifier.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(this));
        ChangeUtil.replaceChild((CompositeElement)child, oldIdentifier, newIdentifier);
      }
    }

    if (psiClass.isEnum()) {
      for (TreeElement child = first; child != afterLast; child = next) {
        next = child.getTreeNext();
        if ((child.getElementType() == ElementType.METHOD && ((PsiMethod)SourceTreeToPsiMap.treeElementToPsi(child)).isConstructor()) ||
            child.getElementType() == ElementType.ENUM_CONSTANT) {
          CompositeElement modifierList = (CompositeElement)((CompositeElement)child).findChildByRole(ChildRole.MODIFIER_LIST);
          while (true) {
            TreeElement modifier = TreeUtil.findChild(modifierList, MODIFIERS_TO_REMOVE_IN_ENUM_BIT_SET);
            if (modifier == null) break;
            modifierList.deleteChildInternal(modifier);
          }
        }
      }
    }
    else if (psiClass.isInterface()) {
      for (TreeElement child = first; child != afterLast; child = next) {
        next = child.getTreeNext();
        if (child.getElementType() == ElementType.METHOD || child.getElementType() == ElementType.FIELD) {
          CompositeElement modifierList = (CompositeElement)((CompositeElement)child).findChildByRole(ChildRole.MODIFIER_LIST);
          while (true) {
            TreeElement modifier = TreeUtil.findChild(modifierList, MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET);
            if (modifier == null) break;
            modifierList.deleteChildInternal(modifier);
          }
        }
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  public boolean isEnum() {
    final TreeElement keyword = findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == ENUM_KEYWORD;
  }

  public boolean isAnnotationType() {
    return findChildByRole(ChildRole.AT) != null;
  }

  private static final TokenSet MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET = TokenSet.create(new IElementType[]{
    PUBLIC_KEYWORD, ABSTRACT_KEYWORD,
    STATIC_KEYWORD, FINAL_KEYWORD,
    NATIVE_KEYWORD
  });

  private static final TokenSet MODIFIERS_TO_REMOVE_IN_ENUM_BIT_SET = TokenSet.create(new IElementType[]{
    PUBLIC_KEYWORD, FINAL_KEYWORD
  });

  private static final TokenSet ENUM_CONSTANT_LIST_ELEMENTS_BIT_SET = TokenSet.create(new IElementType[]{
    ENUM_CONSTANT, COMMA, SEMICOLON
  });


  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        if (firstChild.getElementType() == DOC_COMMENT) {
          return firstChild;
        }
        else {
          return null;
        }

      case ChildRole.ENUM_CONSTANT_LIST_DELIMITER:
        if (!isEnum()) {
          return null;
        }
        return findEnumConstantListDelimiter();

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, MODIFIER_LIST);

      case ChildRole.EXTENDS_LIST:
        if (isAnnotationType() || isEnum()) return null;
        return TreeUtil.findChild(this, EXTENDS_LIST);

      case ChildRole.IMPLEMENTS_LIST:
        return TreeUtil.findChild(this, IMPLEMENTS_LIST);

      case ChildRole.TYPE_PARAMETER_LIST:
        return TreeUtil.findChild(this, TYPE_PARAMETER_LIST);

      case ChildRole.CLASS_OR_INTERFACE_KEYWORD:
        for (TreeElement child = firstChild; child != null; child = child.getTreeNext()) {
          if (CLASS_KEYWORD_BIT_SET.isInSet(child.getElementType())) return child;
        }
        LOG.assertTrue(false);
        return null;

      case ChildRole.NAME:
        return TreeUtil.findChild(this, IDENTIFIER);

      case ChildRole.LBRACE:
        return TreeUtil.findChild(this, LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, RBRACE);

      case ChildRole.AT:
        return TreeUtil.findChild(this, AT);
    }
  }

  private TreeElement findEnumConstantListDelimiter() {
    TreeElement candidate = findEnumConstantListDelimiterPlace();
    return candidate.getElementType() == SEMICOLON ? candidate : null;
  }

  private TreeElement findEnumConstantListDelimiterPlace() {
    final TreeElement first = findChildByRole(ChildRole.LBRACE);
    if (first == null) return null;
    for (TreeElement child = first.getTreeNext(); ; child = child.getTreeNext()) {
      final IElementType childType = child.getElementType();
      if (WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(childType) ||
          childType == ERROR_ELEMENT || childType == ENUM_CONSTANT) continue;
      else if (childType == COMMA) continue;
      else if (childType == SEMICOLON) return child;
      else {
        return TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      }
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == SEMICOLON) {
      if (!isEnum()) return ChildRole.NONE;
      if (child == findEnumConstantListDelimiter()) {
        return ChildRole.ENUM_CONSTANT_LIST_DELIMITER;
      }
      else {
        return ChildRole.NONE;
      }
    }
    else if (i == CLASS) {
      return ChildRole.CLASS;
    }
    else if (i == FIELD) {
      return ChildRole.FIELD;
    }
    else if (i == METHOD || i == ANNOTATION_METHOD) {
      return ChildRole.METHOD;
    }
    else if (i == CLASS_INITIALIZER) {
      return ChildRole.CLASS_INITIALIZER;
    }
    else if (i == TYPE_PARAMETER_LIST) {
      return ChildRole.TYPE_PARAMETER_LIST;
    }
    else if (i == DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == C_STYLE_COMMENT || i == END_OF_LINE_COMMENT) {
      {
        if (TreeUtil.skipElementsBack(child, WHITE_SPACE_OR_COMMENT_BIT_SET) == null) {
          return ChildRole.PRECEDING_COMMENT;
        }
        else {
          return ChildRole.NONE;
        }
      }
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == EXTENDS_LIST) {
      return ChildRole.EXTENDS_LIST;
    }
    else if (i == IMPLEMENTS_LIST) {
      return ChildRole.IMPLEMENTS_LIST;
    }
    else if (i == CLASS_KEYWORD || i == INTERFACE_KEYWORD || i == ENUM_KEYWORD) {
      return getChildRole(child, ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    }
    else if (i == IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == AT) {
      return ChildRole.AT;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
