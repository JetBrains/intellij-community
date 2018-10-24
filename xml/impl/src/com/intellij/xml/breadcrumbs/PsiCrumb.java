// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey.Malenkov
 */
final class PsiCrumb extends Crumb.Impl implements NavigatableCrumb {
  private final PsiAnchor anchor;
  private BreadcrumbsProvider provider;
  private String tooltip;
  final CrumbPresentation presentation;

  PsiCrumb(PsiElement element, BreadcrumbsProvider provider, CrumbPresentation presentation) {
    super(provider.getElementIcon(element), provider.getElementInfo(element), null, provider.getContextActions(element));
    anchor = PsiAnchor.create(element);
    this.provider = provider;
    this.presentation = presentation;
  }

  @Override
  public String getTooltip() {
    if (tooltip == null && provider != null) {
      PsiElement element = getElement(this);
      if (element != null) tooltip = provider.getElementTooltip(element);
      provider = null; // do not try recalculate tooltip
    }
    return tooltip;
  }


  @Override
  public TextRange getHighlightRange() {
    PsiElement element = anchor.retrieve();
    return element != null ? element.getTextRange() : null;
  }

  @Override
  public void navigate(Editor editor, boolean withSelection) {
    PsiElement element = anchor.retrieve();
    if (element == null) return;

    moveEditorCaretTo(editor, element.getTextOffset());

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
