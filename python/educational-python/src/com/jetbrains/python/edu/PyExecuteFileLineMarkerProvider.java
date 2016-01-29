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
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    final RunContextAction runAction = new RunContextAction(DefaultRunExecutor.getRunExecutorInstance());
    final LineMarkerInfo<PsiElement> markerInfo = new LineMarkerInfo<PsiElement>(
      file, file.getTextRange(), AllIcons.Actions.Execute, Pass.UPDATE_OVERRIDEN_MARKERS,
      new Function<PsiElement, String>() {
        @Override
        public String fun(PsiElement e) {
          return "Execute '" + e.getContainingFile().getName() + "'";
        }
      }, null,
      GutterIconRenderer.Alignment.RIGHT) {
      @Nullable
      @Override
      public GutterIconRenderer createGutterRenderer() {
        return new LineMarkerGutterIconRenderer<PsiElement>(this) {
          @Override
          public AnAction getClickAction() {
            return runAction;
          }

          @Nullable
          @Override
          public ActionGroup getPopupMenuActions() {
            final DefaultActionGroup group = new DefaultActionGroup();
            group.add(runAction);
            final PyExecuteFileExtensionPoint[] extensions =
              ApplicationManager.getApplication().getExtensions(PyExecuteFileExtensionPoint.EP_NAME);
            for (PyExecuteFileExtensionPoint extension : extensions) {
              final AnAction action = extension.getRunAction();
              if (action == null) {
                continue;
              }
              group.add(action);
            }
            return group;
          }
        };
      }
    };
    result.add(markerInfo);
  }
}
