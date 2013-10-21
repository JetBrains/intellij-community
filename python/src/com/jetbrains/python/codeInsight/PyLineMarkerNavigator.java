/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class PyLineMarkerNavigator<T extends PsiElement> implements GutterIconNavigationHandler<T> {
  public void navigate(final MouseEvent e, final T elt) {
    final List<NavigatablePsiElement> navElements = new ArrayList<NavigatablePsiElement>();
    Query<T> elementQuery = search(elt);
    if (elementQuery == null) return;
    elementQuery.forEach(new Processor<T>() {
      public boolean process(final T psiElement) {
        if (psiElement instanceof NavigatablePsiElement) {
          navElements.add((NavigatablePsiElement)psiElement);
        }
        return true;
      }
    });
    final NavigatablePsiElement[] methods = navElements.toArray(new NavigatablePsiElement[navElements.size()]);
    PsiElementListNavigator.openTargets(e, methods, getTitle(elt), null, new DefaultPsiElementCellRenderer());
  }

  protected abstract String getTitle(T elt);

  @Nullable
  protected abstract Query<T> search(T elt);
}
