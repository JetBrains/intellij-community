// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;

public class YAMLKeyValueImpl extends YAMLPsiElementImpl implements YAMLKeyValue, PsiDeclaredTarget {
  public static final Icon YAML_KEY_ICON = PlatformIcons.PROPERTY_ICON;

  public YAMLKeyValueImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML key value";
  }

  @Override
  @Nullable
  public PsiElement getKey() {
    PsiElement colon = findChildByType(YAMLTokenTypes.COLON);
    if (colon == null) {
      return null;
    }
    ASTNode node = colon.getNode();
    do {
      node = node.getTreePrev();
    } while(YAMLElementTypes.BLANK_ELEMENTS.contains(PsiUtilCore.getElementType(node)));

    if (node == null || PsiUtilCore.getElementType(node) == YAMLTokenTypes.QUESTION) {
      return null;
    }
    else {
      return node.getPsi();
    }
  }

  @Nullable
  @Override
  public YAMLMapping getParentMapping() {
    return ObjectUtils.tryCast(super.getParent(), YAMLMapping.class);
  }

  @Nullable
  @Override
  public String getName() {
    return getKeyText();
  }

  @Override
  @NotNull
  public String getKeyText() {
    final PsiElement keyElement = getKey();
    if (keyElement == null) {
      return "";
    }

    if (keyElement instanceof YAMLScalar) {
      return ((YAMLScalar)keyElement).getTextValue();
    }
    if (keyElement instanceof YAMLCompoundValue) {
      return ((YAMLCompoundValue)keyElement).getTextValue();
    }

    final String text = keyElement.getText();
    return StringUtil.unquoteString(text);
  }

  @Override
  @Nullable
  public YAMLValue getValue() {
    for (PsiElement child = getLastChild(); child != null; child = child.getPrevSibling()) {
      if (PsiUtilCore.getElementType(child) == YAMLTokenTypes.COLON) {
        return null;
      }
      if (child instanceof YAMLValue) {
        return ((YAMLValue)child);
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getValueText() {
    final YAMLValue value = getValue();
    if (value instanceof YAMLScalar){
      return ((YAMLScalar)value).getTextValue();
    }
    else if (value instanceof YAMLCompoundValue) {
      return ((YAMLCompoundValue)value).getTextValue();
    }
    return "";
  }


  @Override
  public void setValue(@NotNull YAMLValue value) {
    adjustWhitespaceToContentType(value instanceof YAMLScalar);

    if (getValue() != null) {
      getValue().replace(value);
      return;
    }

    final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
    if (isExplicit()) {
      if (findChildByType(YAMLTokenTypes.COLON) == null) {
        add(generator.createColon());
        add(generator.createSpace());
        add(value);
      }
    }
    else {
      add(value);
    }
  }

  private void adjustWhitespaceToContentType(boolean isScalar) {
    assert getKey() != null;
    PsiElement key = getKey();

    if (key.getNextSibling() != null && key.getNextSibling().getNode().getElementType() == YAMLTokenTypes.COLON) {
      key = key.getNextSibling();
    }

    while (key.getNextSibling() != null && !(key.getNextSibling() instanceof YAMLValue)) {
      key.getNextSibling().delete();
    }
    final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
    if (isScalar) {
      addAfter(generator.createSpace(), key);
    }
    else {
      final int indent = YAMLUtil.getIndentToThisElement(this);
      addAfter(generator.createIndent(indent + 2), key);
      addAfter(generator.createEol(), key);
    }
  }

  @NotNull
  @Override
  protected Icon getElementIcon(@IconFlags int flags) {
    return YAML_KEY_ICON;
  }

  @Override
  public ItemPresentation getPresentation() {
    ItemPresentation custom = ItemPresentationProviders.getItemPresentation(this);
    if (custom != null) {
      return custom;
    }
    final YAMLFile yamlFile = (YAMLFile)getContainingFile();
    final PsiElement value = getValue();
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        if (value instanceof YAMLScalar){
          ItemPresentation presentation = ((YAMLScalar)value).getPresentation();
          return presentation != null ? presentation.getPresentableText() : getValueText();
        }
        return getName();
      }

      @Override
      public String getLocationString() {
        return yamlFile.getName();
      }

      @Override
      public Icon getIcon(boolean open) {
        return YAMLKeyValueImpl.this.getIcon(0);
      }
    };
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String newName) throws IncorrectOperationException {
    return YAMLUtil.rename(this, newName);
  }

  /**
   * Provide reference contributor with given method registerReferenceProviders implementation:
   * registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLKeyValue.class), ReferenceProvider);
   */
  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  private boolean isExplicit() {
    final ASTNode child = getNode().getFirstChildNode();
    return child != null && child.getElementType() == YAMLTokenTypes.QUESTION;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitKeyValue(this);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public @Nullable TextRange getNameIdentifierRange() {
    PsiElement key = getKey();
    return key == null ? null : key.getTextRangeInParent();
  }
}
