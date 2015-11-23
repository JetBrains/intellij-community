package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLFileType;
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
  public YAMLCompoundValue getValue() {
    for (PsiElement child = getLastChild(); child != null; child = child.getPrevSibling()) {
      if (child instanceof YAMLCompoundValue) {
        return ((YAMLCompoundValue)child);
      }
    }
    return null;
  }

  @NotNull
  public String getValueText() {
    final YAMLCompoundValue value = getValue();
    if (value == null){
      return "";
    }
    return value.getTextValue();
  }

  // TODO make it make it
  public void setValueText(final String text) {
    final YAMLFile yamlFile =
                (YAMLFile) PsiFileFactory.getInstance(getProject())
                  .createFileFromText("temp." + YAMLFileType.YML.getDefaultExtension(), YAMLFileType.YML,
                                      "foo: " + text, LocalTimeCounter.currentTime(), true);
    final YAMLDocument document = yamlFile.getDocuments().get(0);
    final YAMLPsiElement element = document.getYAMLElements().get(0);
    assert element instanceof YAMLMapping;

    final YAMLKeyValue topKeyValue = ((YAMLMapping)element).getKeyValues().iterator().next();
    getValue().replace(topKeyValue.getValue());
  }

  @Override
  public ItemPresentation getPresentation() {
    final YAMLFile yamlFile = (YAMLFile)getContainingFile();
    final PsiElement value = getValue();
    return new ItemPresentation() {
      public String getPresentableText() {
        if (YAMLUtil.isScalarValue(value)){
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

  @NotNull
  public String getValueIndent() {
    final PsiElement value = getValue();
    if (value != null){
      @SuppressWarnings({"ConstantConditions"})
      final ASTNode node = value.getNode().getTreePrev();
      final IElementType type = node.getElementType();
      if (type == YAMLTokenTypes.WHITESPACE || type == YAMLTokenTypes.INDENT){
        return node.getText();
      }
    }
    return "";
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
