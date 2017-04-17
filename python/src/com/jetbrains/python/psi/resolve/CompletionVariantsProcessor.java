/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.completion.PyClassInsertHandler;
import com.jetbrains.python.codeInsight.completion.PyFunctionInsertHandler;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author yole
 */
public class CompletionVariantsProcessor extends VariantsProcessor {
  private static final int MAX_EAGER_ITEMS = 50;
  private final Map<String, LookupElement> myVariants = new HashMap<>();
  private boolean mySuppressParentheses = false;
  private boolean myAllowLazyLookupItems = false;

  public CompletionVariantsProcessor(PsiElement context) {
    super(context);
  }

  public CompletionVariantsProcessor(PsiElement context,
                                     @Nullable Condition<PsiElement> nodeFilter,
                                     @Nullable Condition<String> nameFilter) {
    super(context, nodeFilter, nameFilter);
  }

  public void suppressParentheses() {
    mySuppressParentheses = true;
  }

  public void allowLazyLookupItems() {
    myAllowLazyLookupItems = true;
  }

  private LookupElement setupItem(LookupElementBuilder item) {
    final PsiElement element = item.getPsiElement();
    if (!myPlainNamesOnly) {
      if (!mySuppressParentheses &&
          element instanceof PyFunction && ((PyFunction)element).getProperty() == null &&
          !PyUtil.hasCustomDecorators((PyFunction)element) &&
          !isSingleArgDecoratorCall(myContext, (PyFunction)element)) {
        final Project project = element.getProject();
        item = item.withInsertHandler(PyFunctionInsertHandler.INSTANCE);
        final TypeEvalContext context = TypeEvalContext.codeCompletion(project, myContext != null ? myContext.getContainingFile() : null);
        final List<PyParameter> parameters = PyUtil.getParameters((PyFunction)element, context);
        final String params = StringUtil.join(parameters, pyParameter -> pyParameter.getName(), ", ");
        item = item.withTailText("(" + params + ")");
      }
      else if (element instanceof PyClass) {
        item = item.withInsertHandler(PyClassInsertHandler.INSTANCE);
      }
    }
    if (element == null) {
      return item;
    }

    PyClass clazz = findContainingClass(element);
    if (clazz != null) {
      return item.withTypeText(clazz.getName());
    }
    else if (myContext == null || !PyUtil.inSameFile(myContext, element)) {
      if (myAllowLazyLookupItems) {
        return new LookupElementWithLazyType(item);
      }
      else {
        return item.withTypeText(resolveCanonicalmportPath(element));
      }
    }
    else {
      return item;
    }
  }


  private static String resolveCanonicalmportPath(@NotNull PsiElement element) {
    String source = null;
    QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, null);
    if (path != null) {
      if (element instanceof PyFile) {
        path = path.removeLastComponent();
      }
      source = path.toString();
    }
    return source;
  }

  @Nullable
  private static PyClass findContainingClass(@NotNull PsiElement element) {
    PyClass cls = null;

    if (element instanceof PyFunction) {
      cls = ((PyFunction)element).getContainingClass();
    }
    else if (element instanceof PyTargetExpression) {
      final PyTargetExpression expr = (PyTargetExpression)element;
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
    return cls;
  }

  private static boolean isSingleArgDecoratorCall(PsiElement elementInCall, PyFunction callee) {
    // special case hack to avoid the need of patching generator3.py
    PyClass containingClass = callee.getContainingClass();
    if (containingClass != null && PyNames.PROPERTY.equals(containingClass.getName()) &&
        PyBuiltinCache.getInstance(elementInCall).isBuiltin(containingClass)) {
      return true;
    }

    if (callee.getParameterList().getParameters().length > 1) {
      return false;
    }
    PyDecorator decorator = PsiTreeUtil.getParentOfType(elementInCall, PyDecorator.class);
    if (decorator == null) {
      return false;
    }
    return PsiTreeUtil.isAncestor(decorator.getCallee(), elementInCall, false);
  }

  protected static LookupElementBuilder setItemNotice(final LookupElementBuilder item, String notice) {
    return item.withTypeText(notice);
  }

  public LookupElement[] getResult() {

    final Collection<LookupElement> variants = postProcessVariants(myVariants.values());
    return variants.toArray(new LookupElement[variants.size()]);
  }

  private
  @NotNull
  Collection<LookupElement> postProcessVariants(Collection<LookupElement> values) {
    if (!myAllowLazyLookupItems) {
      return values;
    }
    if (values.size() <= MAX_EAGER_ITEMS) {
      return values.stream().map((LookupElement item) -> {
        if (item instanceof LookupElementWithLazyType) {
          return ((LookupElementWithLazyType)item).toEager();
        }
        else {
          return item;
        }
      }).collect(Collectors.toList());
    }


    return values;
  }

  public List<LookupElement> getResultList() {
    return new ArrayList<>(postProcessVariants(myVariants.values()));
  }

  @Override
  protected void addElement(String name, PsiElement element) {
    if (PyUtil.isClassPrivateName(name) && !PyUtil.inSameFile(element, myContext)) {
      return;
    }
    myVariants.put(name, setupItem(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(element.getIcon(0))));
  }

  @Override
  protected void addImportedElement(String referencedName, PyElement expr) {
    Icon icon = expr.getIcon(0);
    // things like PyTargetExpression cannot have a general icon, but here we only have variables
    if (icon == null) icon = PlatformIcons.VARIABLE_ICON;
    LookupElement lookupItem = setupItem(LookupElementBuilder.createWithSmartPointer(referencedName, expr).withIcon(icon));
    myVariants.put(referencedName, lookupItem);
  }

  private static class LookupElementWithLazyType extends LookupElementDecorator<LookupElementBuilder> {

    private static final String EMPTY_SENTINEL = new String("");
    private String myCanonicalType = EMPTY_SENTINEL;
    private String myPlaceHolderType = EMPTY_SENTINEL;

    protected LookupElementWithLazyType(LookupElementBuilder delegate) {
      super(delegate);
    }


    private boolean isTypeComputed() {
      return myCanonicalType != EMPTY_SENTINEL;
    }


    public LookupElementBuilder toEager() {
      return getDelegate().withTypeText(getCanonicalType());
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      if (presentation.isReal() || isTypeComputed()) {
        presentation.setTypeText(getCanonicalType());
      }
      else {
        presentation.setTypeText(getPlaceHolderType());
      }
    }

    private String getPlaceHolderType() {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      PsiElement myElement = getPsiElement();
      if (myPlaceHolderType == EMPTY_SENTINEL) {
        myPlaceHolderType = myElement != null ? computePlaceHolderType(myElement) : null;
      }
      return myPlaceHolderType;
    }

    private static String computePlaceHolderType(@NotNull PsiElement element) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) {
        return null;
      }
      QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(containingFile);
      if (qName == null) {
        return null;
      }

      return element instanceof PyFile ? qName.removeLastComponent().toString() : qName.toString();
    }

    public String getCanonicalType() {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      PsiElement myElement = getPsiElement();
      if (myCanonicalType == EMPTY_SENTINEL) {
        myCanonicalType = myElement != null ? resolveCanonicalmportPath(myElement) : null;
      }
      return myCanonicalType;
    }
  }
}
