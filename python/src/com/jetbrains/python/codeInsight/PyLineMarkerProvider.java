package com.jetbrains.python.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

/**
 * @author yole
 */
public class PyLineMarkerProvider implements LineMarkerProvider {
  private static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/gutter/overridingMethod.png");
  private static final Icon OVERRIDDEN_ICON = IconLoader.getIcon("/gutter/overridenMethod.png");

  private static final PyLineMarkerNavigator ourSuperMethodNavigator = new PyLineMarkerNavigator<PsiElement>() {
    protected String getTitle(final PsiElement elt) {
      return "Choose Super Method of " + ((PyFunction)elt.getParent()).getName();
    }

    @Nullable
    protected Query<PsiElement> search(final PsiElement elt) {
      if (!(elt.getParent() instanceof PyFunction)) return null;
      return PySuperMethodsSearch.search((PyFunction)elt.getParent());
    }
  };

  private static final PyLineMarkerNavigator ourSubclassNavigator = new PyLineMarkerNavigator<PyClass>() {
    protected String getTitle(final PsiElement elt) {
      return "Choose Subclass of "+ ((PyClass) elt).getName();
    }

    protected Query<PyClass> search(final PsiElement elt) {
      return PyClassInheritorsSearch.search((PyClass) elt, true);
    }
  };

  private static final PyLineMarkerNavigator ourOverridingMethodNavigator = new PyLineMarkerNavigator<PyFunction>() {
    protected String getTitle(final PsiElement elt) {
      return "Choose Overriding Method of " + ((PyFunction) elt).getName();
    }

    protected Query<PyFunction> search(final PsiElement elt) {
      return PyOverridingMethodsSearch.search((PyFunction) elt, true);
    }
  };

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
      return new LineMarkerInfo(element, element.getTextRange().getStartOffset(), OVERRIDING_METHOD_ICON, Pass.UPDATE_ALL,
                                               null, ourSuperMethodNavigator);
    }
    return null;
  }

  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    Set<PyFunction> functions = new HashSet<PyFunction>();
    for(PsiElement element: elements) {
      if (element instanceof PyClass) {
        collectInheritingClasses((PyClass) element, result);
      }
      else if (element instanceof PyFunction) {
        functions.add((PyFunction)element);
      }
    }
    collectOverridingMethods(functions, result);
  }

  private static void collectInheritingClasses(final PyClass element, final Collection<LineMarkerInfo> result) {
    if (PyClassInheritorsSearch.search(element, false).findFirst() != null) {
      result.add(new LineMarkerInfo(element, element.getTextOffset(), OVERRIDDEN_ICON, Pass.UPDATE_OVERRIDEN_MARKERS,
                                    null, ourSubclassNavigator));
    }
  }

  private static void collectOverridingMethods(final Set<PyFunction> functions, final Collection<LineMarkerInfo> result) {
    Set<PyClass> classes = new HashSet<PyClass>();
    final Set<PyFunction> candidates = new HashSet<PyFunction>(functions);
    for(PyFunction function: functions) {
      PyClass pyClass = function.getContainingClass();
      if (pyClass != null && function.getName() != null) {
        classes.add(pyClass);
      }
      else {
        candidates.remove(function);
      }
    }
    final Set<PyFunction> overridden = new HashSet<PyFunction>();
    for(PyClass pyClass: classes) {
      PyClassInheritorsSearch.search(pyClass, true).forEach(new Processor<PyClass>() {
        public boolean process(final PyClass pyClass) {
          for (Iterator<PyFunction> it = candidates.iterator(); it.hasNext();) {
            PyFunction func = it.next();
            if (pyClass.findMethodByName(func.getName()) != null) {
              overridden.add(func);
              it.remove();
            }
          }
          return !candidates.isEmpty();
        }
      });
      if (candidates.isEmpty()) break;
    }
    for(PyFunction func: overridden) {
      result.add(new LineMarkerInfo(func, func.getTextOffset(), OVERRIDDEN_ICON, Pass.UPDATE_OVERRIDEN_MARKERS, null,
                                    ourOverridingMethodNavigator));
    }
  }
}
