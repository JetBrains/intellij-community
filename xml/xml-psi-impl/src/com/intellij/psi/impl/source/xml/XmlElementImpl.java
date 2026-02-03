// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.util.XmlPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlElementImpl extends CompositePsiElement implements XmlElement {
  public XmlElementImpl(IElementType type) {
    super(type);
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    return XmlPsiUtil.processXmlElements(this, processor, false);
  }

  public XmlElement findElementByTokenType(final IElementType type){
    return XmlPsiUtil.findElement(this, elementType -> elementType == type);
  }

  @Override
  public PsiElement getContext() {
    final XmlElement data = getUserData(INCLUDING_ELEMENT);
    if(data != null) return data;
    return getAstParent();
  }

  private PsiElement getAstParent() {
    return super.getParent();
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    if (!isPhysical()) {
      final XmlElement including = getUserData(INCLUDING_ELEMENT);
      if (including != null) {
        return including;
      }
      PsiElement astParent = getAstParent();
      PsiElement parentNavigation = astParent.getNavigationElement();
      if (parentNavigation.getTextOffset() == getTextOffset()) return parentNavigation;
      return this;
    }
    return super.getNavigationElement();
  }

  @Override
  public PsiElement getParent() {
    return getContext();
  }

  @Override
  public @NotNull Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  protected static @Nullable String getNameFromEntityRef(final CompositeElement compositeElement, final IElementType xmlEntityDeclStart) {
    final ASTNode node = compositeElement.findChildByType(xmlEntityDeclStart);
    if (node == null) return null;
    ASTNode name = node.getTreeNext();

    if (name != null && name.getElementType() == TokenType.WHITE_SPACE) {
      name = name.getTreeNext();
    }

    if (name != null && name.getElementType() == XmlElementType.XML_ENTITY_REF) {
      final StringBuilder builder = new StringBuilder();

      ((XmlElement)name.getPsi()).processElements(new PsiElementProcessor() {
        @Override
        public boolean execute(final @NotNull PsiElement element) {
          builder.append(element.getText());
          return true;
        }
      }, name.getPsi());
      if (!builder.isEmpty()) return builder.toString();
    }
    return null;
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return GlobalSearchScope.allScope(getProject());
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {

    if (super.isEquivalentTo(another)) return true;
    PsiElement element1 = this;

    // TODO: seem to be only necessary for tag dirs equivalents checking.
    if (element1 instanceof XmlTag && another instanceof XmlTag) {
      if (!element1.isPhysical() && !another.isPhysical()) return element1.getText().equals(another.getText());
    }

    return false;
  }

  @Override
  public boolean skipValidation() {
    return skipValidation(this);
  }

  public static boolean skipValidation(@NotNull XmlElement holder) {
    Boolean doNotValidate = DO_NOT_VALIDATE.get(holder);
    if (doNotValidate != null) return doNotValidate;

    OuterLanguageElement element = PsiTreeUtil.getChildOfType(holder, OuterLanguageElement.class);

    if (element == null) {
      // JspOuterLanguageElement is located under XmlText
      for (PsiElement child = holder.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof XmlText) {
          element = PsiTreeUtil.getChildOfType(child, OuterLanguageElement.class);
          if (element != null) {
            break;
          }
        }
      }
    }
    if (element == null) {
      doNotValidate = false;
    } else {
      PsiFile containingFile = holder.getContainingFile();
      doNotValidate = containingFile.getViewProvider().getBaseLanguage() != containingFile.getLanguage();
    }
    holder.putUserData(DO_NOT_VALIDATE, doNotValidate);
    return doNotValidate;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    putUserData(DO_NOT_VALIDATE, null);
  }
}
