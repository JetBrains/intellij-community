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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.relaxNG.model.Define;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Set;

class OverridingDefineRenderer extends GutterIconRenderer {
  private static final Icon ICON = IconLoader.getIcon("/gutter/overridingMethod.png");

  private final Set<Define> mySet;
  private final String myMessage;

  public OverridingDefineRenderer(String message, Set<Define> set) {
    mySet = set;
    myMessage = message;
  }

  @NotNull
  public Icon getIcon() {
    return ICON;
  }

  public boolean isNavigateAction() {
    return true;
  }

  @Nullable
  public AnAction getClickAction() {
    return new MyClickAction();
  }

  @Nullable
  public String getTooltipText() {
    return myMessage;
  }

  private class MyClickAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
      doClickAction(e, mySet, "Go to overridden define");
    }
  }

  static void doClickAction(AnActionEvent e, Collection<Define> set, String title) {
    if (set.size() == 1) {
      final Navigatable n = (Navigatable)set.iterator().next().getPsiElement();
      OpenSourceUtil.navigate(true, n);
    } else {
      final Define[] array = set.toArray(new Define[set.size()]);
      NavigationUtil.getPsiElementPopup(ContainerUtil.map(array, new Function<Define, PsiElement>() {
        public PsiElement fun(Define define) {
          return define.getPsiElement();
        }
      }, PsiElement.EMPTY_ARRAY), title).show(new RelativePoint((MouseEvent)e.getInputEvent()));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    OverridingDefineRenderer that = (OverridingDefineRenderer)o;

    if (myMessage != null ? !myMessage.equals(that.myMessage) : that.myMessage != null) return false;
    if (mySet != null ? !mySet.equals(that.mySet) : that.mySet != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySet != null ? mySet.hashCode() : 0;
    result = 31 * result + (myMessage != null ? myMessage.hashCode() : 0);
    return result;
  }
}