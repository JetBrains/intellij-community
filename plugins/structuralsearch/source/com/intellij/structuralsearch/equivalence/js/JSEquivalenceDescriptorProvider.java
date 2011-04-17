package com.intellij.structuralsearch.equivalence.js;

import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.structuralsearch.equivalence.ChildRole;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptor;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorBuilder;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSEquivalenceDescriptorProvider extends EquivalenceDescriptorProvider {
  private static final IElementType[] VARIABLE_DELIMETERS = {JSTokenTypes.COMMA, JSTokenTypes.SEMICOLON};

  @Override
  public boolean isMyContext(@NotNull PsiElement context) {
    return context.getLanguage().isKindOf(JavascriptLanguage.INSTANCE);
  }

  @Override
  public EquivalenceDescriptor buildDescriptor(@NotNull PsiElement element) {
    final EquivalenceDescriptorBuilder builder = new EquivalenceDescriptorBuilder();

    if (element instanceof JSClass) {
      final JSClass c = (JSClass)element;
      return builder
        .element(c.getNameIdentifier())
        .childrenOptionally(c.getAttributeList())
        .childrenInAnyOrder(c.getExtendsList())
        .childrenInAnyOrder(c.getImplementsList())
        .inAnyOrder(c.getFields())
        .inAnyOrder(c.getFunctions());
    }
    else if (element instanceof JSVariable) {
      final JSVariable v = (JSVariable)element;
      return builder
        .element(v.getNameIdentifier())
        .optionally(v.getTypeElement())
        .optionallyInPattern(v.getInitializer())
        .role(v.getNameIdentifier(), ChildRole.VARIABLE_NAME);
    }
    else if (element instanceof JSFunction) {
      final JSFunction f = (JSFunction)element;
      return builder
        .constant(f.getKind())
        .element(f.getNameIdentifier())
        .childrenOptionally(f.getAttributeList())
        .children(f.getParameterList())
        .optionally(f.getReturnTypeElement())
        .optionallyInPattern(f.getBody())
        .role(f.getNameIdentifier(), ChildRole.FUNCTION_NAME);
    }
    else if (element instanceof JSBlockStatement) {
      return builder.codeBlock(((JSBlockStatement)element).getStatements());
    }
    else if (element instanceof JSParenthesizedExpression) {
      return builder.element(((JSParenthesizedExpression)element).getInnerExpression());
    }

    return null;
  }

  @NotNull
  @Override
  public IElementType[] getVariableDelimeters() {
    return VARIABLE_DELIMETERS;
  }

  @Override
  public TokenSet getLiterals() {
    return JSTokenTypes.LITERALS;
  }

  @Override
  public int getNodeCost(@NotNull PsiElement element) {
    if (element instanceof JSStatement || element instanceof JSFunction || element instanceof JSClass) {
      return 2;
    }
    else if (element instanceof JSExpression) {
      return 1;
    }
    return 0;
  }
}