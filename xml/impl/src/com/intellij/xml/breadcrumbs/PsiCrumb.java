// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.ui.UIBundle;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Malenkov
 */
final class PsiCrumb extends Crumb.Impl implements NavigatableCrumb {
  private final PsiAnchor anchor;
  private volatile BreadcrumbsProvider provider;
  private volatile String tooltip;
  final CrumbPresentation presentation;

  PsiCrumb(PsiElement element, BreadcrumbsProvider provider, CrumbPresentation presentation) {
    super(provider.getElementIcon(element), provider.getElementInfo(element), null, provider.getContextActions(element));
    anchor = PsiAnchor.create(element);
    this.provider = provider;
    this.presentation = presentation;
  }

  @Override
  public String getTooltip() {
    if (provider != null
        && (tooltip == null || getLazyTooltipText().equals(tooltip))) {
      PsiElement element = getElement(this);
      tooltip = element == null ? null
                                : provider.getElementTooltip(element);
      provider = null; // do not try recalculate tooltip
    }
    return tooltip;
  }

  @Override
  public String getTooltipLazy() {
    if (tooltip == null && provider != null) {
      tooltip = getLazyTooltipText();
      ReadAction
        .nonBlocking(() -> getTooltip())
        .coalesceBy(this)
        .expireWith(anchor.getFile().getProject())
        .finishOnUiThread(ModalityState.any(), toolTipText -> {
          IdeTooltipManager.getInstance().updateShownTooltip();
        })
        .submit(NonUrgentExecutor.getInstance())
        .onError(anyError -> tooltip = null);
    }
    return tooltip;
  }

  @NotNull
  private static String getLazyTooltipText() {
    return UIBundle.message("crumbs.calculating.tooltip");
  }

  @Override
  public int getAnchorOffset() {
    PsiElement element = anchor.retrieve();
    return element != null ? element.getTextOffset() : -1;
  }

  @Override
  public TextRange getHighlightRange() {
    PsiElement element = anchor.retrieve();
    return element != null ? element.getTextRange() : null;
  }

  @Override
  public void navigate(@NotNull Editor editor, boolean withSelection) {
    int offset = getAnchorOffset();
    if (offset != -1) {
      moveEditorCaretTo(editor, offset);
    }

    if (withSelection) {
      final TextRange range = getHighlightRange();
      if (range != null) {
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
  }

  private static void moveEditorCaretTo(Editor editor, int offset) {
    if (offset >= 0) {
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  @Nullable
  static PsiElement getElement(Crumb crumb) {
    return crumb instanceof PsiCrumb ? ((PsiCrumb)crumb).anchor.retrieve() : null;
  }

  @Nullable
  static CrumbPresentation getPresentation(Crumb crumb) {
    return crumb instanceof PsiCrumb ? ((PsiCrumb)crumb).presentation : null;
  }
}
