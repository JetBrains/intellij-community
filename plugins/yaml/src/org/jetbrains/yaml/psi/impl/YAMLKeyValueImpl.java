package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.*;

import javax.swing.*;

/**
 * @author oleg
 */
public class YAMLKeyValueImpl extends YAMLPsiElementImpl implements YAMLKeyValue {
  public YAMLKeyValueImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML key value";
  }

  @Nullable
  public PsiElement getKey() {
    final PsiElement result = findChildByType(YAMLTokenTypes.SCALAR_KEY);
    if (result != null) {
      return result;
    }
    if (isExplicit()) {
      return findChildByClass(YAMLCompoundValue.class);
    }
    return null;
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

  @NotNull
  public String getKeyText() {
    final PsiElement keyElement = getKey();
    if (keyElement == null) {
      return "";
    }

    if (keyElement instanceof YAMLCompoundValue) {
      return ((YAMLCompoundValue)keyElement).getTextValue();
    }

    final String text = keyElement.getText();
    return StringUtil.unquoteString(text.substring(0, text.length() - 1));
  }

  @Nullable
  public YAMLValue getValue() {
    for (PsiElement child = getLastChild(); child != null; child = child.getPrevSibling()) {
      if (child instanceof YAMLValue) {
        return ((YAMLValue)child);
      }
    }
    return null;
  }

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

  @Override
  public ItemPresentation getPresentation() {
    final YAMLFile yamlFile = (YAMLFile)getContainingFile();
    final PsiElement value = getValue();
    return new ItemPresentation() {
      public String getPresentableText() {
        if (value instanceof YAMLScalar){
          return getValueText();
        }
        return getName();
      }

      public String getLocationString() {
        return "[" + yamlFile.getName() + "]";
      }

      public Icon getIcon(boolean open) {
        return PlatformIcons.PROPERTY_ICON;
      }
    };
  }

  public PsiElement setName(@NonNls @NotNull String newName) throws IncorrectOperationException {
    return YAMLUtil.rename(this, newName);
  }

  /**
   * Provide reference contributor with given method registerReferenceProviders implementation:
   * registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLKeyValue.class), ReferenceProvider);
   */
  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  private boolean isExplicit() {
    final ASTNode child = getNode().getFirstChildNode();
    return child != null && child.getElementType() == YAMLTokenTypes.QUESTION;
  }
}
