package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLDocument;

import javax.swing.*;

/**
 * @author oleg
 */
public class YAMLDocumentImpl extends YAMLPsiElementImpl implements YAMLDocument {
  public YAMLDocumentImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML document";
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return "YAML document";
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }
}
