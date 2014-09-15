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
package org.jetbrains.plugins.coursecreator;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class RunTestsLineMarker implements LineMarkerProvider {
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
    for (PsiElement element : elements) {
      if (isFirstCodeLine(element)) {
        PsiFile psiFile = element.getContainingFile();
        if (psiFile == null || !psiFile.getName().contains(".answer")) {
          continue;
        }
        result.add(new LineMarkerInfo<PsiElement>(
          element, element.getTextRange(), AllIcons.Actions.Lightning, Pass.UPDATE_OVERRIDEN_MARKERS,
          new Function<PsiElement, String>() {
            @Override
            public String fun(PsiElement e) {
              return "Run tests from file '" + e.getContainingFile().getName() + "'";
            }
          },
          new GutterIconNavigationHandler<PsiElement>() {
            @Override
            public void navigate(MouseEvent e, PsiElement elt) {
              executeCurrentScript(elt);
            }
          },
          GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }

  private static void executeCurrentScript(PsiElement elt) {
    Editor editor = PsiUtilBase.findEditor(elt);
    assert editor != null;

    final ConfigurationContext context =
      ConfigurationContext.getFromContext(DataManager.getInstance().getDataContext(editor.getComponent()));
      CCRunTests.run(context);
  }

  private static boolean isFirstCodeLine(PsiElement element) {
    return element instanceof PyStatement &&
           element.getParent() instanceof PyFile &&
           !isNothing(element) &&
           nothingBefore(element);
  }

  private static boolean nothingBefore(PsiElement element) {
    element = element.getPrevSibling();
    while (element != null) {
      if (!isNothing(element)) {
        return false;
      }
      element = element.getPrevSibling();
    }

    return true;
  }

  private static boolean isNothing(PsiElement element) {
    return (element instanceof PsiComment) || (element instanceof PyImportStatement) || (element instanceof PsiWhiteSpace);
  }
}

