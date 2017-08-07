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
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

/**
 * @author vlan
 */
public class PyiRelatedItemLineMarkerProvider extends RelatedItemLineMarkerProvider {
  // TODO: Create an icon for a related Python stub item
  public static final Icon ICON = AllIcons.Gutter.Unique;

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo> result) {
    if (element instanceof PyFunction || element instanceof PyTargetExpression || element instanceof PyClass) {
      final PsiElement pythonStub = PyiUtil.getPythonStub((PyElement)element);
      if (pythonStub != null) {
        result.add(createLineMarkerInfo(element, pythonStub, "Has stub item"));
      }
      final PsiElement originalElement = PyiUtil.getOriginalElement((PyElement)element);
      if (originalElement != null) {
        result.add(createLineMarkerInfo(element, originalElement, "Stub for item"));
      }
    }
  }

  @NotNull
  private static RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element,
                                                                            @NotNull PsiElement relatedElement,
                                                                            @NotNull String itemTitle) {
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(element.getProject());
    final SmartPsiElementPointer<PsiElement> relatedElementPointer = pointerManager.createSmartPsiElementPointer(relatedElement);
    final String stubFileName = relatedElement.getContainingFile().getName();
    return new RelatedItemLineMarkerInfo<>(
      element, element.getTextRange(), ICON, Pass.LINE_MARKERS,
      element1 -> itemTitle + " in " + stubFileName, new GutterIconNavigationHandler<PsiElement>() {
      @Override
      public void navigate(MouseEvent e, PsiElement elt) {
        final PsiElement restoredRelatedElement = relatedElementPointer.getElement();
        if (restoredRelatedElement == null) {
          return;
        }
        final int offset = restoredRelatedElement instanceof PsiFile ? -1 : restoredRelatedElement.getTextOffset();
        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(restoredRelatedElement);
        if (virtualFile != null && virtualFile.isValid()) {
          new OpenFileDescriptor(restoredRelatedElement.getProject(), virtualFile, offset).navigate(true);
        }
      }
    }, GutterIconRenderer.Alignment.RIGHT, GotoRelatedItem.createItems(Collections.singletonList(relatedElement)));
  }
}
