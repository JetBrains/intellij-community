/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.pyi;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class PyiRelatedItemLineMarkerProvider extends RelatedItemLineMarkerProvider {
  // TODO: Create an icon for a related Python stub item
  public static final Icon ICON = AllIcons.Gutter.Unique;

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
    final PsiElement pythonStub = getPythonStub(element);
    if (pythonStub != null) {
      final List<GotoRelatedItem> relatedItems = GotoRelatedItem.createItems(Collections.singletonList(pythonStub));
      result.add(new RelatedItemLineMarkerInfo<PsiElement>(
        element, element.getTextRange(), ICON, Pass.LINE_MARKERS,
        element1 -> "Has stub item in " + pythonStub.getContainingFile().getName(), new GutterIconNavigationHandler<PsiElement>() {
          @Override
          public void navigate(MouseEvent e, PsiElement elt) {
            final PsiElement pythonStub = getPythonStub(elt);
            if (pythonStub != null) {
              PsiNavigateUtil.navigate(pythonStub);
            }
          }
        }, GutterIconRenderer.Alignment.RIGHT, relatedItems));
    }
  }

  @Nullable
  private static PsiElement getPythonStub(@NotNull PsiElement element) {
    if (element instanceof PyFunction || element instanceof PyTargetExpression) {
      return PyiUtil.getPythonStub((PyElement)element);
    }
    return null;
  }
}
