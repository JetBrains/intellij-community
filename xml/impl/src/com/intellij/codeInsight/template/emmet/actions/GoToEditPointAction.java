/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;

/**
 * @author Dennis.Ushakov
 */
public abstract class GoToEditPointAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    final Editor editor = getEditor(e);
    final PsiFile file = getFile(e);
    final boolean isApplicable = editor != null && EmmetEditPointUtil.isApplicableFile(file);
    e.getPresentation().setEnabledAndVisible(isApplicable);
  }

  private static PsiFile getFile(AnActionEvent e) {
    return CommonDataKeys.PSI_FILE.getData(e.getDataContext());
  }

  private static Editor getEditor(AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    return editor != null ? InjectedLanguageUtil.getTopLevelEditor(editor) : null;
  }

  public static class Forward extends GoToEditPointAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      EmmetEditPointUtil.moveForward(getEditor(e), getFile(e));
    }
  }

  public static class Backward extends GoToEditPointAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      EmmetEditPointUtil.moveBackward(getEditor(e), getFile(e));
    }
  }
}
