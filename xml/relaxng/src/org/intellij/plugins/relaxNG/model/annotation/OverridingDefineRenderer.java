/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.annotation;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.model.Define;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

class OverridingDefineRenderer extends GutterIconRenderer implements DumbAware {

  private final Set<? extends Define> mySet;
  private final @Tooltip String myMessage;

  OverridingDefineRenderer(@Tooltip String message, Set<? extends Define> set) {
    mySet = set;
    myMessage = message;
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Gutter.OverridingMethod;
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @Override
  public @Nullable AnAction getClickAction() {
    return new MyClickAction();
  }

  @Override
  public @Nullable String getTooltipText() {
    return myMessage;
  }

  private class MyClickAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      doClickAction(e, mySet, RelaxngBundle.message("relaxng.gutter.go-to-overridden-define"));
    }
  }

  static void doClickAction(AnActionEvent e, Collection<? extends Define> set, @PopupTitle String title) {
    if (set.size() == 1) {
      final Navigatable n = (Navigatable)set.iterator().next().getPsiElement();
      OpenSourceUtil.navigate(true, n);
    }
    else {
      final Define[] array = set.toArray(new Define[0]);
      NavigationUtil.getPsiElementPopup(ContainerUtil.map(array, define -> define.getPsiElement(), PsiElement.EMPTY_ARRAY), title)
        .show(new RelativePoint((MouseEvent)e.getInputEvent()));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    OverridingDefineRenderer that = (OverridingDefineRenderer)o;

    if (!Objects.equals(myMessage, that.myMessage)) return false;
    if (!Objects.equals(mySet, that.mySet)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySet != null ? mySet.hashCode() : 0;
    result = 31 * result + (myMessage != null ? myMessage.hashCode() : 0);
    return result;
  }
}
