package com.intellij.sh.lexer;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.psi.impl.ShBlockImpl;
import org.jetbrains.annotations.NotNull;

import static com.intellij.sh.ShTypes.LEFT_CURLY;
import static com.intellij.sh.ShTypes.RIGHT_CURLY;

public class ShLazyBlockElementType extends IReparseableElementType implements ICompositeElementType {
  public ShLazyBlockElementType(@NotNull String debugName) {
    super(debugName, ShLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return createNode(null);
  }

  @NotNull
  @Override
  public ASTNode createNode(CharSequence text) {
    return new ShBlockImpl(this, text);
  }

  @Override
  public boolean isParsable(@NotNull CharSequence buffer, @NotNull Language fileLanguage, @NotNull Project project) {
    return PsiBuilderUtil.hasProperBraceBalance(buffer, new ShLexer(), LEFT_CURLY, RIGHT_CURLY);
  }
}
