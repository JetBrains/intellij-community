/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class NavigationGutterIconRenderer extends GutterIconRenderer
  implements GutterIconNavigationHandler<PsiElement>, DumbAware {
  protected final @PopupTitle String myPopupTitle;
  private final @PopupContent String myEmptyText;
  protected final Computable<? extends PsiElementListCellRenderer> myCellRenderer;
  private final NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> myPointers;

  protected NavigationGutterIconRenderer(final @PopupTitle String popupTitle,
                                         final @PopupContent String emptyText,
                                         @NotNull Computable<? extends PsiElementListCellRenderer<?>> cellRenderer,
                                         @NotNull NotNullLazyValue<? extends List<SmartPsiElementPointer<?>>> pointers) {
    myPopupTitle = popupTitle;
    myEmptyText = emptyText;
    myCellRenderer = cellRenderer;
    myPointers = pointers;
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @NotNull
  public List<PsiElement> getTargetElements() {
    List<SmartPsiElementPointer<?>> pointers = myPointers.getValue();
    if (pointers.isEmpty()) return Collections.emptyList();
    Project project = pointers.get(0).getProject();
    DumbService dumbService = DumbService.getInstance(project);
    return dumbService.computeWithAlternativeResolveEnabled(() -> ContainerUtil.mapNotNull(pointers, smartPsiElementPointer -> smartPsiElementPointer.getElement()));
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final NavigationGutterIconRenderer renderer = (NavigationGutterIconRenderer)o;

    if (myEmptyText != null ? !myEmptyText.equals(renderer.myEmptyText) : renderer.myEmptyText != null) return false;
    if (!myPointers.getValue().equals(renderer.myPointers.getValue())) return false;
    if (myPopupTitle != null ? !myPopupTitle.equals(renderer.myPopupTitle) : renderer.myPopupTitle != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myPopupTitle != null ? myPopupTitle.hashCode() : 0;
    result = 31 * result + (myEmptyText != null ? myEmptyText.hashCode() : 0);
    result = 31 * result + myPointers.getValue().hashCode();
    return result;
  }

  @Override
  @Nullable
  public AnAction getClickAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        navigate((MouseEvent)e.getInputEvent(), null);
      }
    };
  }

  @Override
  public void navigate(@Nullable final MouseEvent event, @Nullable final PsiElement elt) {
    List<PsiElement> list = getTargetElements();
    if (list.isEmpty()) {
      if (myEmptyText != null) {
        if (event != null) {
          final JComponent label = HintUtil.createErrorLabel(myEmptyText);
          label.setBorder(JBUI.Borders.empty(2, 7, 2, 7));
          JBPopupFactory.getInstance().createBalloonBuilder(label)
            .setFadeoutTime(3000)
            .setFillColor(HintUtil.getErrorColor())
            .createBalloon()
            .show(new RelativePoint(event), Balloon.Position.above);
        }
      }
    }
    else {
      navigateToItems(event);
    }
  }

  protected void navigateToItems(@Nullable MouseEvent event) {
    List<Navigatable> navigatables = new ArrayList<>();
    for (SmartPsiElementPointer<?> pointer : myPointers.getValue()) {
       ContainerUtil.addIfNotNull(navigatables, getNavigatable(pointer));
    }
    if (navigatables.size() == 1) {
      navigatables.get(0).navigate(true);
    }
    else if (event != null) {
      PsiElement[] elements = PsiUtilCore.toPsiElementArray(getTargetElements());
      JBPopup popup = NavigationUtil.getPsiElementPopup(elements, myCellRenderer.compute(), myPopupTitle);
      popup.show(new RelativePoint(event));
    }
  }

  @Nullable
  private static Navigatable getNavigatable(SmartPsiElementPointer<?> pointer) {
    Navigatable element = getNavigationElement(pointer);
    if (element != null) return element;

    VirtualFile virtualFile = pointer.getVirtualFile();
    Segment actualRange = pointer.getRange();
    if (virtualFile != null && actualRange != null && virtualFile.isValid() && actualRange.getStartOffset() >= 0) {
      return new OpenFileDescriptor(pointer.getProject(), virtualFile, actualRange.getStartOffset());
    }

    return null;
  }

  @Nullable
  private static Navigatable getNavigationElement(SmartPsiElementPointer<?> pointer) {
    PsiElement element = pointer.getElement();
    if (element == null) return null;
    final PsiElement navigationElement = element.getNavigationElement();
    return navigationElement instanceof Navigatable ? (Navigatable)navigationElement : null;
  }
}
