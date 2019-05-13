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
package com.jetbrains.python.codeInsight.userSkeletons;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.PsiNavigateUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author vlan
 */
public class PyUserSkeletonsLineMarkerProvider implements LineMarkerProvider {
  // TODO: Create an icon for a related user skeleton
  public static final Icon ICON = AllIcons.Gutter.Unique;

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
    for (PsiElement element : elements) {
      final PyElement skeleton = getUserSkeleton(element);
      if (skeleton != null) {
        PsiElement identifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
        if (identifier != null) {
          result.add(new LineMarkerInfo<>(
            identifier, identifier.getTextRange(), ICON, Pass.LINE_MARKERS,
            __ -> "Has user skeleton",
            (__, elt) -> {
              final PyElement s = getUserSkeleton(elt.getParent());
              if (s != null) {
                PsiNavigateUtil.navigate(s);
              }
            },
            GutterIconRenderer.Alignment.RIGHT));
        }
      }
    }
  }

  @Nullable
  private static PyElement getUserSkeleton(@NotNull PsiElement element) {
    if (element instanceof PyFunction || element instanceof PyTargetExpression) {
      return PyUserSkeletonsUtil.getUserSkeleton((PyElement)element);
    }
    return null;
  }
}
