/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyLineSeparatorUtil {

  private PyLineSeparatorUtil() {
  }

  public interface Provider {
    boolean isSeparatorAllowed(PsiElement element);
  }

  @Nullable
  public static LineMarkerInfo addLineSeparatorIfNeeded(final Provider provider,
                                                        final PsiElement element) {
    final Ref<LineMarkerInfo> info = new Ref<LineMarkerInfo>(null);
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!provider.isSeparatorAllowed(element)) {
        return;
      }
      boolean hasSeparableBefore = false;
      final PsiElement parent = element.getParent();
      if (parent == null) {
        return;
      }
      for (PsiElement child : parent.getChildren()) {
        if (child == element){
          break;
        }
        if (provider.isSeparatorAllowed(child)) {
          hasSeparableBefore = true;
          break;
        }
      }
      if (!hasSeparableBefore) {
        return;
      }
      info.set(createLineSeparatorByElement(element));
    });
    return info.get();
  }

  private static LineMarkerInfo<PsiElement> createLineSeparatorByElement(final PsiElement element) {
    final LineMarkerInfo<PsiElement> info = new LineMarkerInfo<PsiElement>(element, element.getTextRange().getStartOffset(), null, Pass.LINE_MARKERS, null, null);
    info.separatorColor = EditorColorsManager.getInstance().getGlobalScheme().getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    info.separatorPlacement = SeparatorPlacement.TOP;
    return info;
  }

}
