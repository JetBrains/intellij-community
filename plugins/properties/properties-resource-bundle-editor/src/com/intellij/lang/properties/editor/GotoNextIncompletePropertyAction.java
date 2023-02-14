// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class GotoNextIncompletePropertyAction extends AnAction {
  private final static Logger LOG = Logger.getInstance(GotoNextIncompletePropertyAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ResourceBundleEditor editor = (ResourceBundleEditor)e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    LOG.assertTrue(editor != null);
    editor.selectNextIncompleteProperty();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformCoreDataKeys.FILE_EDITOR) instanceof ResourceBundleEditor);
  }
}