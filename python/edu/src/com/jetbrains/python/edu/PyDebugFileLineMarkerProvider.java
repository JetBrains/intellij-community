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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class PyDebugFileLineMarkerProvider implements LineMarkerProvider {
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
     for (final PsiElement element : elements) {
      if (PyEduUtils.isFirstCodeLine(element)) {
        result.add(new LineMarkerInfo<PsiElement>(element, element.getTextRange(), AllIcons.Actions.StartDebugger,
                                                  Pass.UPDATE_OVERRIDEN_MARKERS,
                                                  new Function<PsiElement, String>() {
                                                    @Override
                                                    public String fun(PsiElement e) {
                                                      return "Debug '" + e.getContainingFile().getName() + "'";
                                                    }
                                                  }, new GutterIconNavigationHandler<PsiElement>() {
          @Override
          public void navigate(MouseEvent e, PsiElement elt) {
            final Editor editor = PsiUtilBase.findEditor(elt);
            assert editor != null;
            ConfigurationContext configurationContext =
              ConfigurationContext.getFromContext(DataManager.getInstance().getDataContext(editor.getComponent()));
            new PyDebugCurrentFileAction().run(configurationContext);
          }
        }, GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }
}
