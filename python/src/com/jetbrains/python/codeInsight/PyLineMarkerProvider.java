// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectionQuery;
import com.intellij.util.Function;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public final class PyLineMarkerProvider implements LineMarkerProvider, PyLineSeparatorUtil.Provider {

  private static final class TooltipProvider implements Function<PsiElement, @NlsContexts.Tooltip String> {
    private final String myText;

    private TooltipProvider(@NlsContexts.Tooltip String text) {
      myText = text;
    }

    @Override
    public String fun(PsiElement psiElement) {
      return myText;
    }
  }

  private static final int INHERITORS_LIMIT = 10;

  private static final Function<PsiElement, @NlsContexts.Tooltip String> ourSubclassTooltipProvider = identifier -> {
    PsiElement parent = identifier.getParent();
    if (!(parent instanceof PyClass pyClass)) return null;
    final HtmlBuilder builder = new HtmlBuilder();
    builder.append(PyBundle.message("line.markers.tooltip.header.is.subclassed.by"));
    final AtomicInteger count = new AtomicInteger();
    PyClassInheritorsSearch.search(pyClass, true).forEach(inheritor -> {
      String className = inheritor.getName();
      if (className == null) return true;
      if (count.incrementAndGet() >= INHERITORS_LIMIT) {
        return false;
      }
      builder.br().nbsp(2).append(className);
      return true;
    });
    boolean tooManySubclasses = count.get() >= INHERITORS_LIMIT;
    return tooManySubclasses ? PyBundle.message("line.markers.tooltip.has.subclasses") : builder.wrapWithHtmlBody().toString();
  };

  private static final Function<PsiElement, @NlsContexts.Tooltip String> ourOverridingMethodTooltipProvider = element -> {
    PsiElement parent = element.getParent();
    if (!(parent instanceof PyFunction pyFunction)) return "";
    final HtmlBuilder builder = new HtmlBuilder();
    builder.append(PyBundle.message("line.markers.tooltip.header.is.overridden.in"));
    final AtomicInteger count = new AtomicInteger();

    PyClassInheritorsSearch.search(pyFunction.getContainingClass(), true).forEach(pyClass -> {
      String className = pyClass.getName();
      if (className == null) return true;
      if (count.incrementAndGet() >= INHERITORS_LIMIT) {
        return false;
      }
      if (pyClass.findMethodByName(pyFunction.getName(), false, null) != null) {
        builder.br().nbsp(2).append(className);
      }
      return true;
    });
    boolean tooManyOverrides = count.get() >= INHERITORS_LIMIT;
    return tooManyOverrides ? PyBundle.message("line.markers.tooltip.has.overridden.methods") : builder.wrapWithHtmlBody().toString();
  };

  private static final PyLineMarkerNavigator<PsiElement> SUPER_METHOD_NAVIGATOR = new PyLineMarkerNavigator<>() {
    @Override
    protected @PopupTitle String getTitle(@NotNull PsiElement nameIdentifier) {
      return PyBundle.message("line.markers.popup.title.choose.super.method", ((PyFunction)nameIdentifier.getParent()).getName());
    }

    @Override
    @Nullable
    protected Query<PsiElement> search(@NotNull PsiElement nameIdentifier, @NotNull final TypeEvalContext context) {
      if (!(nameIdentifier.getParent() instanceof PyFunction)) return null;
      return PySuperMethodsSearch.search((PyFunction)nameIdentifier.getParent(), context);
    }
  };

  private static final PyLineMarkerNavigator<PsiElement> SUPER_ATTRIBUTE_NAVIGATOR = new PyLineMarkerNavigator<>() {
    @Override
    protected @PopupTitle String getTitle(@NotNull PsiElement nameIdentifier) {
      return PyBundle
        .message("line.markers.popup.title.choose.super.attribute", ((PyTargetExpression)nameIdentifier.getParent()).getName());
    }

    @Override
    @Nullable
    protected Query<PsiElement> search(@NotNull PsiElement nameIdentifier, @NotNull TypeEvalContext context) {
      if (!(nameIdentifier.getParent() instanceof PyTargetExpression)) return null;
      final List<PsiElement> result = new ArrayList<>();
      final PyClass containingClass = PsiTreeUtil.getParentOfType(nameIdentifier, PyClass.class);
      if (containingClass != null) {
        for (PyClass ancestor : containingClass.getAncestorClasses(context)) {
          final PyTargetExpression attribute = ancestor.findClassAttribute(nameIdentifier.getText(), false, context);
          if (attribute != null) {
            result.add(attribute);
          }
        }
      }
      return new CollectionQuery<>(result);
    }
  };

  private static final PyLineMarkerNavigator<PsiElement> ourSubclassNavigator = new PyLineMarkerNavigator<>() {
    @Override
    protected @PopupTitle String getTitle(final PsiElement elt) {
      PsiElement parent = elt.getParent();
      return parent instanceof PyClass ? PyBundle.message("line.markers.popup.title.choose.subclass", ((PyClass)parent).getName()) : "";
    }

    @Nullable
    @Override
    protected Query<? extends PsiElement> search(PsiElement elt, @NotNull TypeEvalContext context) {
      PsiElement parent = elt.getParent();
      return parent instanceof PyClass ? PyClassInheritorsSearch.search((PyClass)parent, true) : null;
    }
  };

  private static final PyLineMarkerNavigator<PsiElement> ourOverridingMethodNavigator = new PyLineMarkerNavigator<>() {
    @Override
    protected @PopupTitle String getTitle(PsiElement element) {
      PsiElement parent = element.getParent();
      if (parent instanceof PyFunction) {
        return PyBundle.message("line.markers.popup.title.choose.overriding.method", ((PyFunction)parent).getName());
      }
      return "";
    }

    @Override
    protected Query<? extends PsiElement> search(final PsiElement element, @NotNull TypeEvalContext context) {
      PsiElement parent = element.getParent();
      return parent instanceof PyFunction ? PyOverridingMethodsSearch.search((PyFunction)parent, true) : null;
    }
  };

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(final @NotNull PsiElement element) {
    IElementType elementType = element.getNode().getElementType();
    if (elementType == PyTokenTypes.IDENTIFIER && element.getParent() instanceof PyFunction function) {
      return getMethodMarker(element, function);
    }
    if (element instanceof PyTargetExpression && PyUtil.isClassAttribute(element)) {
      return getAttributeMarker((PyTargetExpression)element);
    }

    // Separators are registered only on the first leaf element of a declaration
    if (!DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS || element.getFirstChild() != null) {
      return null;
    }
    PyElement parentDeclaration = PsiTreeUtil.getParentOfType(element, PyFunction.class, PyClass.class);
    if (parentDeclaration == null || element != PsiTreeUtil.getDeepestFirst(parentDeclaration)) {
      return null;
    }
    if (isSeparatorAllowed(parentDeclaration)) {
      return PyLineSeparatorUtil.addLineSeparatorIfNeeded(this, parentDeclaration);
    }
    return null;
  }

  @Override
  public boolean isSeparatorAllowed(@Nullable PsiElement element) {
    if (element == null || element.getContainingFile().getVirtualFile() instanceof BackedVirtualFile) return false;

    if (element instanceof PyClass) {
      return PyUtil.isTopLevel(element);
    }
    else if (element instanceof PyFunction) {
      if (PyUtil.isTopLevel(element)) return true;
      PyClass containingClass = ((PyFunction)element).getContainingClass();
      if (containingClass != null && PyUtil.isTopLevel(containingClass)) return true;
    }
    return false;
  }

  @Nullable
  private static LineMarkerInfo<PsiElement> getMethodMarker(final PsiElement identifier, final PyFunction function) {
    if (PyUtil.isInitMethod(function)) {
      return null;
    }
    final TypeEvalContext context = TypeEvalContext.codeAnalysis(identifier.getProject(), function.getContainingFile());
    final PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
    if (superMethod != null) {
      PyClass superClass = null;
      if (superMethod instanceof PyFunction) {
        superClass = ((PyFunction)superMethod).getContainingClass();
      }
      // TODO: show "implementing" instead of "overriding" icon for Python implementations of Java interface methods
      return new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridingMethod,
                                  superClass == null ? null : new TooltipProvider(PyBundle.message(
                                    "line.markers.tooltip.overrides.method.in.class", superClass.getName())),
                                  SUPER_METHOD_NAVIGATOR, GutterIconRenderer.Alignment.RIGHT);
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
          return new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridingMethod,
                                      new TooltipProvider(
                                        PyBundle.message("line.markers.tooltip.overrides.attribute.in.class", ancestor.getName())), SUPER_ATTRIBUTE_NAVIGATOR,
                                      GutterIconRenderer.Alignment.RIGHT);
        }
      }
    }
    return null;
  }

  @Override
  public void collectSlowLineMarkers(final @NotNull List<? extends PsiElement> elements, final @NotNull Collection<? super LineMarkerInfo<?>> result) {
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

  private static void collectInheritingClasses(final PyClass element, final Collection<? super LineMarkerInfo<?>> result) {
    if (PyClassInheritorsSearch.search(element, false).findFirst() != null) {
      PsiElement identifier = element.getNameIdentifier();
      if (identifier != null) {
        result.add(new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridenMethod, ourSubclassTooltipProvider,
                                        ourSubclassNavigator, GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }

  private static void collectOverridingMethods(final Set<? extends PyFunction> functions, final Collection<? super LineMarkerInfo<?>> result) {
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
        result.add(new LineMarkerInfo<>(identifier, identifier.getTextRange(), AllIcons.Gutter.OverridenMethod,
                                        ourOverridingMethodTooltipProvider, ourOverridingMethodNavigator,
                                        GutterIconRenderer.Alignment.RIGHT));
      }
    }
  }
}
