// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyLineSeparatorUtil {

  private PyLineSeparatorUtil() {
  }

  public interface Provider {
    boolean isSeparatorAllowed(@Nullable PsiElement element);
  }

  @Nullable
  public static LineMarkerInfo addLineSeparatorIfNeeded(@NotNull Provider provider, @NotNull PsiElement element) {
    final Ref<LineMarkerInfo> info = new Ref<>(null);
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!provider.isSeparatorAllowed(element)) {
        return;
      }
      //boolean hasSeparableBefore = false;
      //final PsiElement parent = element.getParent();
      //if (parent == null) {
      //  return;
      //}
      //for (PsiElement child : parent.getChildren()) {
      //  if (child == element){
      //    break;
      //  }
      //  if (provider.isSeparatorAllowed(child)) {
      //    hasSeparableBefore = true;
      //    break;
      //  }
      //}
      //if (!hasSeparableBefore) {
      //  return;
      //}
      PsiElement nextChild = PsiTreeUtil.nextChildAfter(element);
      if (nextChild != null) {
        info.set(createLineSeparatorByElement(element, nextChild));
      }
    });
    return info.get();
  }

  @NotNull
  private static LineMarkerInfo<PsiElement> createLineSeparatorByElement(@NotNull PsiElement element,
                                                                         @NotNull PsiElement nextChild) {
    PsiElement anchor = PsiTreeUtil.getDeepestLast(element);
    return LineMarkersPass.createMethodSeparatorLineMarker(anchor,
                                                           new TextRange(anchor.getTextRange().getEndOffset(),
                                                                         nextChild.getTextRange().getStartOffset()),
                                                           EditorColorsManager.getInstance());
  }
}
