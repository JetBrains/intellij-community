package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.tree.IElementType;
import de.plushnikov.intellij.plugin.psi.LombokPsiModifierListImpl;
import org.jetbrains.annotations.NotNull;

public class LombokJavaParserDefinition extends JavaParserDefinition {
  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    final IElementType elementType = node.getElementType();

    if (elementType == JavaStubElementTypes.MODIFIER_LIST) {
      final ASTNode treeParent = node.getTreeParent();
      if (null != treeParent &&
          (treeParent.getElementType() == JavaStubElementTypes.CLASS ||
              treeParent.getElementType() == JavaStubElementTypes.FIELD)) {
        return new LombokPsiModifierListImpl(node);
      }
    }

    return super.createElement(node);
  }
}
