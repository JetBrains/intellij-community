package com.jetbrains.python.edu;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.PopupFactoryImpl;
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
    for (PsiElement element : elements) {
      if (PyEduUtils.isFirstCodeLine(element)) {
        final LineMarkerInfo<PsiElement> markerInfo = new LineMarkerInfo<PsiElement>(
          element, element.getTextRange(), AllIcons.Actions.Execute, Pass.UPDATE_OVERRIDEN_MARKERS,
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
            return new LineMarkerGutterIconRenderer<PsiElement>(this){
              @Override
              public AnAction getClickAction() {

                return new AnAction() {
                  @Override
                  public void actionPerformed(@NotNull AnActionEvent e) {
                    final DefaultActionGroup group = new DefaultActionGroup();
                    group.add(new PyRunCurrentFileAction());
                    final PyExecuteFileExtensionPoint[] extensions =
                      ApplicationManager.getApplication().getExtensions(PyExecuteFileExtensionPoint.EP_NAME);
                    for (PyExecuteFileExtensionPoint extension : extensions) {
                      final AnAction action = extension.getRunAction();
                      action.update(e);
                      if (e.getPresentation().isEnabled())
                        group.add(action);
                    }
                    if (group.getChildrenCount() == 1) {
                      new PyRunCurrentFileAction().actionPerformed(e);
                    }
                    else {
                      final ListPopup popup =
                        new PopupFactoryImpl().createActionGroupPopup(null, group, e.getDataContext(), false, false, false, null, 5);
                      popup.showInBestPositionFor(e.getDataContext());
                    }
                  }
                };
              }

              @Nullable
              @Override
              public ActionGroup getPopupMenuActions() {
                final DefaultActionGroup group = new DefaultActionGroup();
                group.add(new PyRunCurrentFileAction());
                final PyExecuteFileExtensionPoint[] extensions =
                  ApplicationManager.getApplication().getExtensions(PyExecuteFileExtensionPoint.EP_NAME);
                for (PyExecuteFileExtensionPoint extension : extensions) {
                  final AnAction action = extension.getRunAction();
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
  }
}
