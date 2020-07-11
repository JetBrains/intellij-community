// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory;
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MarkdownCodeFenceImpl extends CompositePsiElement implements PsiLanguageInjectionHost, MarkdownPsiElement {
  public MarkdownCodeFenceImpl(IElementType type) {
    super(type);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitCodeFence(this);
      return;
    }

    super.accept(visitor);
  }


  @Nullable
  public String getFenceLanguage() {
    final PsiElement element = findPsiChildByType(MarkdownTokenTypes.FENCE_LANG);
    if (element == null) {
      return null;
    }
    return element.getText();
  }

  @Override
  public ItemPresentation getPresentation() {
    return new MarkdownBasePresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        if (!isValid()) return null;

        return "Code Fence";
      }

      @Nullable
      @Override
      public String getLocationString() {
        if (!isValid()) return null;

        final StringBuilder sb = new StringBuilder();

        List<PsiElement> elements = MarkdownCodeFenceUtils.getContent(MarkdownCodeFenceImpl.this, false);
        if (elements == null) return "";

        for (PsiElement element : elements) {
          if (sb.length() > 0) {
            sb.append("\\n");
          }
          sb.append(element.getText());

          if (sb.length() >= MarkdownCompositePsiElementBase.PRESENTABLE_TEXT_LENGTH) {
            break;
          }
        }

        return sb.toString();
      }
    };
  }

  @Override
  public boolean isValidHost() {
    return MarkdownCodeFenceUtils.isAbleToAcceptInjections(this);
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return ElementManipulators.handleContentChange(this, text);
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new LiteralTextEscaper<PsiLanguageInjectionHost>(this) {
      @Override
      public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
        List<PsiElement> elements = MarkdownCodeFenceUtils.getContent((MarkdownCodeFenceImpl)this.myHost, false);
        if (elements == null) return true;
        for (PsiElement element: elements) {
          outChars.append(element.getText());
        }

        return true;
      }

      @Override
      public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        List<PsiElement> elements = MarkdownCodeFenceUtils.getContent((MarkdownCodeFenceImpl)this.myHost, false);
        if (elements == null) return -1;
        int cur = 0;
        for (PsiElement element: elements) {
          if (cur + element.getTextLength() == offsetInDecoded) {
            return element.getStartOffsetInParent() + element.getTextLength();
          } else if (cur == offsetInDecoded) {
            return element.getStartOffsetInParent();
          } else if (cur < offsetInDecoded &&  (cur + element.getTextLength()) > offsetInDecoded) {
            return element.getStartOffsetInParent() + (offsetInDecoded - cur);
          }
          cur += element.getTextLength();
        }

        PsiElement last = elements.get(elements.size() - 1);

        int result = last.getStartOffsetInParent() + (offsetInDecoded - (cur - last.getTextLength()));
        if (rangeInsideHost.getStartOffset() <= result && result <=rangeInsideHost.getEndOffset()) {
          return result;
        }

        return -1;
      }

      @NotNull
      @Override
      public TextRange getRelevantTextRange() {
        List<PsiElement> elements = MarkdownCodeFenceUtils.getContent((MarkdownCodeFenceImpl)this.myHost, true);

        if (elements == null) {
          return MarkdownCodeFenceUtils.getEmptyRange((MarkdownCodeFenceImpl) this.myHost);
        }

        final PsiElement first = elements.get(0);
        final PsiElement last = elements.get(elements.size() - 1);

        return TextRange.create(first.getStartOffsetInParent(), last.getStartOffsetInParent() + last.getTextLength());
      }

      @Override
      public boolean isOneLine() {
        return false;
      }
    };
  }

  public static class Manipulator extends AbstractElementManipulator<MarkdownCodeFenceImpl> {
    @Override
    public MarkdownCodeFenceImpl handleContentChange(@NotNull MarkdownCodeFenceImpl element, @NotNull TextRange range, String content)
      throws IncorrectOperationException {
      if (content == null) {
        return null;
      }

      if (content.contains("```") || content.contains("~~~")) {
        MarkdownPsiElement textElement = MarkdownPsiElementFactory.createTextElement(element.getProject(), content);
        return textElement instanceof MarkdownCodeFenceImpl ? (MarkdownCodeFenceImpl)element.replace(textElement) : null;
      }

      String indent = MarkdownCodeFenceUtils.getIndent(element);

      if (indent != null && indent.length() > 0) {
        content = Arrays.stream(StringUtil.splitByLinesKeepSeparators(content))
          .map(line -> indent + line)
          .collect(Collectors.joining(""));

        if (StringUtil.endsWithLineBreak(content)) {
          content += indent;
        }
      }

      return (MarkdownCodeFenceImpl)element.replace(
        MarkdownPsiElementFactory.createCodeFence(
          element.getProject(), element.getFenceLanguage(), Objects.requireNonNull(content), indent)
      );
    }
  }
}
