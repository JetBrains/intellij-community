/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.NullableFunction;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author peter
 */
public abstract class NavigationGutterIconRenderer extends GutterIconRenderer implements GutterIconNavigationHandler<PsiElement>{
  private final String myPopupTitle;
  private final String myEmptyText;
  private final PsiElementListCellRenderer myCellRenderer;
  private final NotNullLazyValue<List<SmartPsiElementPointer>> myPointers;

  protected NavigationGutterIconRenderer(final String popupTitle, final String emptyText, final PsiElementListCellRenderer cellRenderer,
                                         final NotNullLazyValue<List<SmartPsiElementPointer>> pointers) {
    myPopupTitle = popupTitle;
    myEmptyText = emptyText;
    myCellRenderer = cellRenderer;
    myPointers = pointers;
  }

  public boolean isNavigateAction() {
    return true;
  }

  public List<PsiElement> getTargetElements() {
    return ContainerUtil.mapNotNull(myPointers.getValue(), new NullableFunction<SmartPsiElementPointer, PsiElement>() {
      public PsiElement fun(final SmartPsiElementPointer smartPsiElementPointer) {
        return smartPsiElementPointer.getElement();
      }
    });
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final NavigationGutterIconRenderer renderer = (NavigationGutterIconRenderer)o;

    if (myCellRenderer != null ? !myCellRenderer.equals(renderer.myCellRenderer) : renderer.myCellRenderer != null) return false;
    if (myEmptyText != null ? !myEmptyText.equals(renderer.myEmptyText) : renderer.myEmptyText != null) return false;
    if (!myPointers.getValue().equals(renderer.myPointers.getValue())) return false;
    if (myPopupTitle != null ? !myPopupTitle.equals(renderer.myPopupTitle) : renderer.myPopupTitle != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myPopupTitle != null ? myPopupTitle.hashCode() : 0);
    result = 31 * result + (myEmptyText != null ? myEmptyText.hashCode() : 0);
    result = 31 * result + (myCellRenderer != null ? myCellRenderer.hashCode() : 0);
    result = 31 * result + myPointers.getValue().hashCode();
    return result;
  }

  public PsiElementListCellRenderer getCellRenderer() {
    return myCellRenderer;
  }

  @Nullable
  public AnAction getClickAction() {
    return new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        navigate(e == null ? null : (MouseEvent)e.getInputEvent(), null);
      }
    };
  }

  public void navigate(final MouseEvent event, final PsiElement elt) {
    final List<PsiElement> list = getTargetElements();
    if (list.isEmpty()) {
      if (myEmptyText != null) {
        final JLabel renderer = HintUtil.createErrorLabel(myEmptyText);
        final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(renderer, renderer).createPopup();
        if (event != null) {
          popup.show(new RelativePoint(event));
        }
      }
      return;
    }
    if (list.size() == 1) {
      PsiNavigateUtil.navigate(list.iterator().next());
    }
    else {
      final JBPopup popup = NavigationUtil.getPsiElementPopup(list.toArray(new PsiElement[list.size()]), myCellRenderer, myPopupTitle);
      if (event != null) {
        popup.show(new RelativePoint(event));
      }
    }
  }
}
