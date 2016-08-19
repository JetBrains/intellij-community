package com.jetbrains.python.edu;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class PyExecuteFileLineMarkerProvider implements LineMarkerProvider {
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
    if (elements.isEmpty()) {
      return;
    }
    PsiElement file = elements.get(0).getContainingFile();
    final RunContextAction runAction = new PyStudyRunContextAction(DefaultRunExecutor.getRunExecutorInstance());
    final PyExecuteFileExtensionPoint[] extensions =
      ApplicationManager.getApplication().getExtensions(PyExecuteFileExtensionPoint.EP_NAME);

    final List<AnAction> actions = new ArrayList<>();
    final DefaultActionGroup group = new DefaultActionGroup();
    if (PlatformUtils.isPyCharmEducational()) {
      group.add(runAction);
    }
    for (PyExecuteFileExtensionPoint extension : extensions) {
      AnAction action = extension.getRunAction();
      if (action != null && extension.accept(file.getProject())) {
        actions.add(action);
        group.add(action);
      }
    }

    if (actions.isEmpty() && !PlatformUtils.isPyCharmEducational()) {
      return;
    }

    Icon icon = PlatformUtils.isPyCharmEducational() ? AllIcons.Actions.Execute : actions.get(0).getTemplatePresentation().getIcon();
    final LineMarkerInfo<PsiElement> markerInfo = new LineMarkerInfo<PsiElement>(
      file, file.getTextRange(), icon, Pass.LINE_MARKERS,
      e -> {
        String text = "Execute '" + e.getContainingFile().getName() + "'";
        return PlatformUtils.isPyCharmEducational() ? text : actions.get(0).getTemplatePresentation().getText();
      }, null,
      GutterIconRenderer.Alignment.RIGHT) {
      @Nullable
      @Override
      public GutterIconRenderer createGutterRenderer() {
        return new LineMarkerGutterIconRenderer<PsiElement>(this) {
          @Override
          public AnAction getClickAction() {
            return PlatformUtils.isPyCharmEducational() ? runAction : actions.get(0);
          }

          @Nullable
          @Override
          public ActionGroup getPopupMenuActions() {
            if (!PlatformUtils.isPyCharmEducational() && actions.isEmpty()) {
              return null;
            }
            if (actions.size() == 1) {
              return null;
            }
            return group;
          }
        };
      }
    };
    result.add(markerInfo);
  }
}
