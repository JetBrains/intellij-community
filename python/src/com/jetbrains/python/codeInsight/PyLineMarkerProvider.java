package com.jetbrains.python.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkerProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyLineMarkerProvider implements LineMarkerProvider {
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");
  private static final Icon OVERRIDDEN_ICON = IconLoader.getIcon("/gutter/overridenMethod.png");

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
      PyLineMarkerNavigator markerNavigator = new PyLineMarkerNavigator<PsiElement>() {
        protected String getTitle(final PsiElement elt) {
          return "Choose Super Method of " + ((PyFunction)elt.getParent()).getName();
        }

        @Nullable
        protected Query<PsiElement> search(final PsiElement elt) {
          if (!(elt.getParent() instanceof PyFunction)) return null;
          return PySuperMethodsSearch.search((PyFunction)elt.getParent());
        }
      };
      return new LineMarkerInfo(element, element.getTextRange().getStartOffset(), OVERRIDING_METHOD_ICON, Pass.UPDATE_ALL,
                                               null, markerNavigator);
    }
    return null;
  }

  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    for(PsiElement element: elements) {
      if (element instanceof PyClass) {
        collectInheritingClasses((PyClass) element, result);
      }
    }
  }

  private static void collectInheritingClasses(final PyClass element, final Collection<LineMarkerInfo> result) {
    if (PyClassInheritorsSearch.search(element, false).findFirst() != null) {
      PyLineMarkerNavigator navigator = new PyLineMarkerNavigator<PyClass>() {
        protected String getTitle(final PsiElement elt) {
          return "Choose Subclass of "+ ((PyClass) elt).getName();
        }

        protected Query<PyClass> search(final PsiElement elt) {
          return PyClassInheritorsSearch.search((PyClass) elt, true);
        }
      };
      result.add(new LineMarkerInfo(element, element.getTextOffset(), OVERRIDDEN_ICON, Pass.UPDATE_OVERRIDEN_MARKERS,
                                    null, navigator));
    }
  }
}
