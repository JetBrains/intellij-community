package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.java.stubs.JavaModifierListElementType;
import com.intellij.psi.tree.IElementType;
import de.plushnikov.intellij.plugin.psi.LombokPsiModifierListImpl;
import org.jetbrains.annotations.NotNull;

public class LombokJavaParserDefinition extends JavaParserDefinition {
  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {

    final IElementType type = node.getElementType();
    if (type instanceof JavaModifierListElementType) {
      return new LombokPsiModifierListImpl(node);
    }

    //TODO SourceStubPsiFactory.createModifierList

    return super.createElement(node);
  }
}
