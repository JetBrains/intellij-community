package com.jetbrains.python.edu;

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
      if (isFirstCodeLine(element)) {
        result.add(new LineMarkerInfo<PsiElement>(
          element, element.getTextRange(), AllIcons.Actions.Execute, Pass.UPDATE_OVERRIDEN_MARKERS,
          new Function<PsiElement, String>() {
            @Override
            public String fun(PsiElement e) {
              return "Execute '" + e.getContainingFile().getName() + "'";
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
    PyRunCurrentFileAction.run(context);
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
