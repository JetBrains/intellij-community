package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.CommentLiteralEscaper;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.ide.util.EditSourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, PsiJavaToken, PsiLanguageInjectionHost, Navigatable {
  public PsiCommentImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitComment(this);
  }

  public String toString(){
    return "PsiComment(" + getElementType().toString() + ")";
  }

  @Nullable
  public List<Pair<PsiElement, TextRange>> getInjectedPsi() {
    return InjectedLanguageUtil.getInjectedPsiFiles(this);
  }

  public void fixText(@NotNull final String text) {
    ChangeUtil.changeElementInPlace(this, new ChangeUtil.ChangeAction() {
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        setText(text);
      }
    });
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(true);
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this); // js var maybe defined via comment annotation
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @NotNull
  public LiteralTextEscaper createLiteralTextEscaper() {
    return new CommentLiteralEscaper(this);
  }

  public void processInjectedPsi(@NotNull InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
  }
}
