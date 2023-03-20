// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.IconManager;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.completion.PyClassInsertHandler;
import com.jetbrains.python.codeInsight.completion.PyFunctionInsertHandler;
import com.jetbrains.python.codeInsight.completion.PythonCompletionWeigher;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.mlcompletion.PyCompletionMlElementInfo;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;


public class CompletionVariantsProcessor extends VariantsProcessor {

  @NotNull
  private final Map<String, LookupElement> myVariants = new HashMap<>();

  private final boolean mySuppressParentheses;

  public CompletionVariantsProcessor(PsiElement context) {
    super(context);
    mySuppressParentheses = false;
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Condition<PsiElement> nodeFilter,
                                     @Nullable Condition<String> nameFilter) {
    super(context, nodeFilter, nameFilter);
    mySuppressParentheses = false;
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Condition<PsiElement> nodeFilter,
                                     @Nullable Condition<String> nameFilter,
                                     boolean plainNamesOnly,
                                     boolean suppressParentheses) {
    super(context, nodeFilter, nameFilter, plainNamesOnly);
    mySuppressParentheses = suppressParentheses;
  }

  @NotNull
  private LookupElement setupItem(@NotNull LookupElementBuilder item) {
    final PsiElement element = item.getPsiElement();
    if (!myPlainNamesOnly && element != null) {
      final Project project = element.getProject();
      final TypeEvalContext context = TypeEvalContext.codeCompletion(project, myContext != null ? myContext.getContainingFile() : null);

      if (!mySuppressParentheses &&
          element instanceof PyFunction && ((PyFunction)element).getProperty() == null &&
          !PyKnownDecoratorUtil.hasUnknownDecorator((PyFunction)element, context) &&
          !isSingleArgDecoratorCall(myContext, (PyFunction)element)) {
        item = item.withInsertHandler(PyFunctionInsertHandler.INSTANCE);
        final List<PyCallableParameter> parameters = ((PyFunction)element).getParameters(context);
        final String params = StringUtil.join(parameters, PyCallableParameter::getName, ", ");
        item = item.withTailText("(" + params + ")");
      }
      else if (element instanceof PyClass) {
        item = item.withInsertHandler(PyClassInsertHandler.INSTANCE);
      }
    }
    String source = null;
    if (element != null) {
      PyClass cls = null;

      if (element instanceof PyFunction) {
        cls = ((PyFunction)element).getContainingClass();
      }
      else if (element instanceof PyTargetExpression expr) {
        if (expr.isQualified() || ScopeUtil.getScopeOwner(expr) instanceof PyClass) {
          cls = expr.getContainingClass();
        }
      }
      else if (element instanceof PyClass) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
        if (owner instanceof PyClass) {
          cls = (PyClass)owner;
        }
      }

      if (cls != null) {
        source = cls.getName();
      }
      else if (myContext == null || !PyUtil.inSameFile(myContext, element)) {
        final PsiFileSystemItem fileSystemItem = PyPsiUtils.getFileSystemItem(element);
        final QualifiedName path = QualifiedNameFinder.findShortestImportableQName(fileSystemItem);
        if (path != null) {
          source = ObjectUtils.chooseNotNull(QualifiedNameFinder.canonizeQualifiedName(fileSystemItem, path, null), path).toString();
        }
      }
    }
    if (source != null) {
      item = item.withTypeText(source);
    }

    final PsiElement parent = myContext != null ? myContext.getParent() : null;
    if (parent instanceof PyKeywordArgument) {
      final String keyword = ((PyKeywordArgument)parent).getKeyword();
      if (item.getLookupString().equals(keyword)) {
        return PrioritizedLookupElement.withPriority(item, PythonCompletionWeigher.PRIORITY_WEIGHT);
      }
    }

    PyCompletionMlElementInfo info = PyCompletionMlElementInfo.Companion.fromElement(element);
    item.putUserData(PyCompletionMlElementInfo.Companion.getKey(), info);
    return item;
  }

  private static boolean isSingleArgDecoratorCall(@Nullable PsiElement elementInCall, @NotNull PyFunction callee) {
    // special case hack to avoid the need of patching generator3.py
    final PyClass containingClass = callee.getContainingClass();
    if (containingClass != null && PyNames.PROPERTY.equals(containingClass.getName()) &&
        PyBuiltinCache.getInstance(elementInCall).isBuiltin(containingClass)) {
      return true;
    }

    if (callee.getParameterList().getParameters().length > 1) {
      return false;
    }
    final PyDecorator decorator = PsiTreeUtil.getParentOfType(elementInCall, PyDecorator.class);
    if (decorator == null) {
      return false;
    }
    return PsiTreeUtil.isAncestor(decorator.getCallee(), elementInCall, false);
  }

  public LookupElement @NotNull [] getResult() {
    final Collection<LookupElement> variants = myVariants.values();
    return variants.toArray(LookupElement.EMPTY_ARRAY);
  }

  @NotNull
  public List<LookupElement> getResultList() {
    return new ArrayList<>(myVariants.values());
  }

  @Override
  protected void addElement(@NotNull String name, @NotNull PsiElement element) {
    markAsProcessed(name);
    myVariants.put(name, setupItem(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(element.getIcon(0))));
  }

  @Override
  protected void addImportedElement(@NotNull String name, @NotNull PyElement element) {
    Icon icon = element.getIcon(0);
    // things like PyTargetExpression cannot have a general icon, but here we only have variables
    if (icon == null) icon = IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable);
    markAsProcessed(name);
    myVariants.put(name, setupItem(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(icon)));
  }
}
