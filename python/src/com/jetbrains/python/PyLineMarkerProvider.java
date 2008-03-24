package com.jetbrains.python;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkerProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Collection;

/**
 * @author yole
 */
public class PyLineMarkerProvider implements LineMarkerProvider {
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");

  public LineMarkerInfo getLineMarkerInfo(final PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null && node.getElementType() == PyTokenTypes.IDENTIFIER && element.getParent() instanceof PyFunction) {
      final PyFunction function = (PyFunction)element.getParent();
      return getMethodMarker(element, function);
    }
    return null;
  }

  @Nullable
  private static LineMarkerInfo getMethodMarker(final PsiElement element, final PyFunction function) {
    if (PySuperMethodsSearch.search(function).findFirst() != null) {
      // TODO: show "implementing" instead of "overriding" icon for Python implementations of Java interface methods
      LineMarkerInfo info = new LineMarkerInfo(element, element.getTextRange().getStartOffset(),
                                               OVERRIDING_METHOD_ICON, Pass.UPDATE_ALL, null, new PyLineMarkerNavigator());
      return info;
    }
    return null;
  }

  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
  }
}
