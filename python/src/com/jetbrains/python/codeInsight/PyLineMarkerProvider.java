/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Function;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yole
 */
public class PyLineMarkerProvider implements LineMarkerProvider, PyLineSeparatorUtil.Provider {

  private static class TooltipProvider implements Function<PsiElement, String> {
    private final String myText;

    private TooltipProvider(String text) {
      myText = text;
    }

    @Override
    public String fun(PsiElement psiElement) {
      return myText;
    }
  }

  private static final Function<PsiElement, String> ourSubclassTooltipProvider = identifier -> {
    PsiElement parent = identifier.getParent();
    if (!(parent instanceof PyClass)) return null;
    final StringBuilder builder = new StringBuilder("<html>Is subclassed by:");
    final AtomicInteger count = new AtomicInteger();
    PyClass pyClass = (PyClass)parent;
    PyClassInheritorsSearch.search(pyClass, true).forEach(pyClass1 -> {
      if (count.incrementAndGet() >= 10) {
        builder.setLength(0);
        builder.append("Has subclasses");
        return false;
      }
      builder.append("<br>&nbsp;&nbsp;").append(pyClass1.getName());
      return true;
    });
    return builder.toString();
  };

  private static final Function<PsiElement, String> ourOverridingMethodTooltipProvider = element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PyFunction)) return "";
    final StringBuilder builder = new StringBuilder("<html>Is overridden in:");
    final AtomicInteger count = new AtomicInteger();
    PyFunction pyFunction = (PyFunction)parent;

    PyClassInheritorsSearch.search(pyFunction.getContainingClass(), true).forEach(pyClass -> {
      if (count.incrementAndGet() >= 10) {
        builder.setLength(0);
        builder.append("Has overridden methods");
        return false;
      }
      if (pyClass.findMethodByName(pyFunction.getName(), false, null) != null) {
        builder.append("<br>&nbsp;&nbsp;").append(pyClass.getName());
      }
      return true;
    });
    return builder.toString();
  };

  private static final PyLineMarkerNavigator<PsiElement> ourSuperMethodNavigator = new PyLineMarkerNavigator<PsiElement>() {
    @Override
    protected String getTitle(final PsiElement elt) {
      return "Choose Super Method of " + ((PyFunction)elt.getParent()).getName();
    }

    @Override
    @Nullable
    protected Query<PsiElement> search(final PsiElement elt, @NotNull final TypeEvalContext context) {
      if (!(elt.getParent() instanceof PyFunction)) return null;
      return PySuperMethodsSearch.search((PyFunction)elt.getParent(), context);
    }
  };

  private static final PyLineMarkerNavigator<PsiElement> ourSuperAttributeNavigator = new PyLineMarkerNavigator<PsiElement>() {
    @Override
    protected String getTitle(final PsiElement elt) {
      return "Choose Super Attribute of " + ((PyTargetExpression)elt).getName();
    }

    @Override
    @Nullable
    protected Query<PsiElement> search(final PsiElement elt, @NotNull final TypeEvalContext context) {
      List<PsiElement> result = new ArrayList<>();
      PyClass containingClass = PsiTreeUtil.getParentOfType(elt, PyClass.class);
      if (containingClass != null && elt instanceof PyTargetExpression) {
        for (PyClass ancestor : containingClass.getAncestorClasses(context)) {
          final PyTargetExpression attribute = ancestor.findClassAttribute(((PyTargetExpression)elt).getReferencedName(), false, context);
          if (attribute != null) {
            result.add(attribute);
          }
        }
      }
      return new CollectionQuery<>(result);
    }
  };

  private static final PyLineMarkerNavigator<PsiElement> ourSubclassNavigator = new PyLineMarkerNavigator<PsiElement>() {
    @Override
    protected String getTitle(final PsiElement elt) {
      PsiElement parent = elt.getParent();
      return parent instanceof PyClass  ? "Choose Subclass of " + ((PyClass)parent).getName() : "";
    }

    @Nullable
    @Override
    protected Query<? extends PsiElement> search(PsiElement elt, @NotNull TypeEvalContext context) {
      PsiElement parent = elt.getParent();
      return parent instanceof PyClass ? PyClassInheritorsSearch.search((PyClass)parent, true) : null;
    }
  };

  private static final PyLineMarkerNavigator<PsiElement> ourOverridingMethodNavigator = new PyLineMarkerNavigator<PsiElement>() {
    @Override
    protected String getTitle(PsiElement element) {
      PsiElement parent = element.getParent();
      return parent instanceof PyFunction ? "Choose Overriding Method of " + ((PyFunction)parent).getName() : "";
    }

    @Override
    protected Query<? extends PsiElement> search(final PsiElement element, @NotNull TypeEvalContext context) {
      PsiElement parent = element.getParent();
      return parent instanceof PyFunction ? PyOverridingMethodsSearch.search((PyFunction)parent, true) : null;
    }
  };

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null && node.getElementType() == PyTokenTypes.IDENTIFIER && element.getParent() instanceof PyFunction) {
      final PyFunction function = (PyFunction)element.getParent();
      return getMethodMarker(element, function);
    }
    if (element instanceof PyTargetExpression && PyUtil.isClassAttribute(element)) {
      return getAttributeMarker((PyTargetExpression)element);
    }
    if (DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS && isSeparatorAllowed(element)) {
      return PyLineSeparatorUtil.addLineSeparatorIfNeeded(this, element);
    }
    return null;
  }

  @Override
  public boolean isSeparatorAllowed(PsiElement element) {
    return element instanceof PyFunction || element instanceof PyClass;
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> getMethodMarker(final PsiElement identifier, final PyFunction function) {
    if (PyNames.INIT.equals(function.getName())) {
      return null;
    }
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(identifier.getProject(), (function != null ? function.getContainingFile() : null));
    final PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
    if (superMethod != null) {
      PyClass superClass = null;
      if (superMethod instanceof PyFunction) {
        superClass = ((PyFunction)superMethod).getContainingClass();
      }
      // TODO: show "implementing" instead of "overriding" icon for Python implementations of Java interface methods
      return new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridingMethod,
                                            Pass.LINE_MARKERS,
                                            superClass == null ? null : new TooltipProvider("Overrides method in " + superClass.getName()),
                                            ourSuperMethodNavigator,GutterIconRenderer.Alignment.RIGHT);
    }
    return null;
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> getAttributeMarker(PyTargetExpression element) {
    final String name = element.getReferencedName();
    if (name == null) {
      return null;
    }
    PyClass containingClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
    if (containingClass == null) return null;
    for (PyClass ancestor : containingClass
      .getAncestorClasses(TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile()))) {
      final PyTargetExpression ancestorAttr = ancestor.findClassAttribute(name, false, null);
      if (ancestorAttr != null) {
        PsiElement identifier = element.getNameIdentifier();
        if (identifier != null) {
          return new LineMarkerInfo<>(identifier, identifier.getTextRange(),
                                      AllIcons.Gutter.OverridingMethod, Pass.LINE_MARKERS,
                                      new TooltipProvider("Overrides attribute in " + ancestor.getName()),
                                      ourSuperAttributeNavigator, GutterIconRenderer.Alignment.RIGHT);
        }
      }
    }
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull final List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    Set<PyFunction> functions = new HashSet<>();
    for (PsiElement element : elements) {
      if (element instanceof PyClass) {
        collectInheritingClasses((PyClass)element, result);
      }
      else if (element instanceof PyFunction) {
        functions.add((PyFunction)element);
      }
    }
    collectOverridingMethods(functions, result);
  }

  private static void collectInheritingClasses(final PyClass element, final Collection<LineMarkerInfo> result) {
    if (PyClassInheritorsSearch.search(element, false).findFirst() != null) {
      PsiElement identifier = element.getNameIdentifier();
      if (identifier != null) {
        result.add(new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridenMethod, Pass.LINE_MARKERS,
                                    ourSubclassTooltipProvider, ourSubclassNavigator, GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }

  private static void collectOverridingMethods(final Set<PyFunction> functions, final Collection<LineMarkerInfo> result) {
    Set<PyClass> classes = new HashSet<>();
    final MultiMap<PyClass, PyFunction> candidates = new MultiMap<>();
    for (PyFunction function : functions) {
      PyClass pyClass = function.getContainingClass();
      if (pyClass != null && function.getName() != null) {
        classes.add(pyClass);
        candidates.putValue(pyClass, function);
      }
    }
    final Set<PyFunction> overridden = new HashSet<>();
    for (final PyClass pyClass : classes) {
      PyClassInheritorsSearch.search(pyClass, true).forEach(inheritor -> {
        for (Iterator<PyFunction> it = candidates.get(pyClass).iterator(); it.hasNext(); ) {
          PyFunction func = it.next();
          if (inheritor.findMethodByName(func.getName(), false, null) != null) {
            overridden.add(func);
            it.remove();
          }
        }
        return !candidates.isEmpty();
      });
      if (candidates.isEmpty()) break;
    }
    for (PyFunction func : overridden) {
      PsiElement identifier = func.getNameIdentifier();
      if (identifier != null) {
        result.add(new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridenMethod, Pass.LINE_MARKERS,
                                        ourOverridingMethodTooltipProvider,
                                        ourOverridingMethodNavigator, GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }
}
