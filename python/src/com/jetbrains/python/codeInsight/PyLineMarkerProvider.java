package com.jetbrains.python.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

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

  private static final PyLineMarkerNavigator<PyFunction> ourOverridingMethodNavigator = new PyLineMarkerNavigator<PyFunction>() {
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
    final Map<PyClass, Collection<PyFunction>> candidates = new HashMap<PyClass, Collection<PyFunction>>();
    final Set<PyFunction> overridden = new HashSet<PyFunction>();
    // group up the methods by class
    for(PyFunction function: functions) {
      PyClass pyClass = function.getContainingClass();
      if (pyClass != null && function.getName() != null) {
        Collection<PyFunction> methods = candidates.get(pyClass);
        if (methods == null) {
          methods = new ArrayList<PyFunction>();
          candidates.put(pyClass, methods);
        }
        methods.add(function);
      }
    }
    // for every class, ascend ancestry levels and see if a function is defined 
    for (PyClass pyClass : candidates.keySet()) {
      for (PyClass granny : pyClass.iterateAncestors()) {
        for (PyFunction func : candidates.get(pyClass)) {
          final String func_name = func.getName();
          assert func_name != null;
          if (granny.findMethodByName(func_name) != null) {
            overridden.add(func);
          }
        }
      }
    }

    for(PyFunction func: overridden) {
      result.add(new LineMarkerInfo<PyFunction>(func, func.getTextOffset(), OVERRIDDEN_ICON, Pass.UPDATE_OVERRIDEN_MARKERS, null,
                                    ourOverridingMethodNavigator));
    }
  }
}
