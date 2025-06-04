// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.NameSuggesterUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.changeSignature.PyChangeSignatureDialog;
import com.jetbrains.python.refactoring.changeSignature.PyMethodDescriptor;
import com.jetbrains.python.refactoring.changeSignature.PyParameterInfo;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

import static com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER;
import static com.jetbrains.python.psi.PyUtil.as;

public final class PyChangeSignatureQuickFix extends LocalQuickFixOnPsiElement {

  public static final Key<Boolean> CHANGE_SIGNATURE_ORIGINAL_CALL = Key.create("CHANGE_SIGNATURE_ORIGINAL_CALL");

  public static @NotNull PyChangeSignatureQuickFix forMismatchedCall(@NotNull PyArgumentsMapping mapping) {
    assert mapping.getCallableType() != null;
    final PyFunction function = as(mapping.getCallableType().getCallable(), PyFunction.class);
    assert function != null;
    Supplier<List<Pair<Integer, PyParameterInfo>>> extraParamsSupplier = () -> {
      final PyCallSiteExpression callSiteExpression = mapping.getCallSiteExpression();
      int positionalParamAnchor = -1;
      final PyParameter[] parameters = function.getParameterList().getParameters();
      for (PyParameter parameter : parameters) {
        final PyNamedParameter namedParam = parameter.getAsNamed();
        final boolean isVararg = namedParam != null && (namedParam.isPositionalContainer() || namedParam.isKeywordContainer());
        if (parameter instanceof PySingleStarParameter || parameter.hasDefaultValue() || isVararg) {
          break;
        }
        positionalParamAnchor++;
      }
      final List<Pair<Integer, PyParameterInfo>> newParameters = new ArrayList<>();
      final TypeEvalContext context = TypeEvalContext.userInitiated(function.getProject(), callSiteExpression.getContainingFile());
      final Set<String> usedParamNames = new HashSet<>();
      for (PyExpression arg : mapping.getUnmappedArguments()) {
        if (arg instanceof PyKeywordArgument) {
          final PyExpression value = ((PyKeywordArgument)arg).getValueExpression();
          final String valueText = value != null ? value.getText() : "";
          newParameters.add(Pair.create(parameters.length - 1,
                                        new PyParameterInfo(NEW_PARAMETER, ((PyKeywordArgument)arg).getKeyword(), valueText, true)));
        }
        else {
          final String paramName = generateParameterName(arg, function, usedParamNames, context);
          newParameters.add(Pair.create(positionalParamAnchor, new PyParameterInfo(NEW_PARAMETER, paramName, arg.getText(), false)));
          usedParamNames.add(paramName);
        }
      }
      return newParameters;
    };
    return new PyChangeSignatureQuickFix(function, extraParamsSupplier, mapping.getCallSiteExpression());
  }

  public static @NotNull PyChangeSignatureQuickFix forMismatchingMethods(@NotNull PyFunction function, @NotNull PyFunction complementary) {
    Supplier<List<Pair<Integer, PyParameterInfo>>> extraParamsSupplier = () -> {
      final int paramLength = function.getParameterList().getParameters().length;
      final int complementaryParamLength = complementary.getParameterList().getParameters().length;
      final List<Pair<Integer, PyParameterInfo>> extraParams;
      if (complementaryParamLength > paramLength) {
        extraParams = Collections.singletonList(Pair.create(paramLength - 1, new PyParameterInfo(NEW_PARAMETER, "**kwargs", "", false)));
      }
      else {
        extraParams = Collections.emptyList();
      }
      return extraParams;
    };
    return new PyChangeSignatureQuickFix(function, extraParamsSupplier, null);
  }

  private final @NotNull Supplier<List<Pair<Integer, PyParameterInfo>>> myExtraParametersSupplier;
  private final @Nullable SmartPsiElementPointer<PyCallSiteExpression> myOriginalCallSiteExpression;


  /**
   * @param extraParametersSupplier supplies new parameters anchored by indexes of the existing parameters they should be inserted <em>after</em>
   *                                (-1 in case they should precede the first parameter)
   */
  private PyChangeSignatureQuickFix(@NotNull PyFunction function,
                                    @NotNull Supplier<List<Pair<Integer, PyParameterInfo>>> extraParametersSupplier,
                                    @Nullable PyCallSiteExpression expression) {
    super(function);
    myExtraParametersSupplier = () -> ContainerUtil.sorted(extraParametersSupplier.get(), Comparator.comparingInt(p -> p.getFirst()));
    if (expression != null) {
      myOriginalCallSiteExpression = SmartPointerManager.getInstance(function.getProject()).createSmartPsiElementPointer(expression);
    }
    else {
      myOriginalCallSiteExpression = null;
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyBundle.message("QFIX.NAME.change.signature");
  }

  @Override
  public @NotNull String getText() {
    final PyFunction function = getFunction();
    if (function == null) {
      return getFamilyName();
    }
    List<PyParameterInfo> parameters = new PyMethodDescriptor(function).getParameters();
    final String params = StringUtil.join(
      parameters,
      info -> info.isNew() ? PyBundle.message("QFIX.bold.html.text", info.getName()) : info.getName(),
      ", "
    );

    final String message = PyBundle.message("QFIX.change.signature.of", StringUtil.notNullize(function.getName()) + "(" + params + ")");
    return XmlStringUtil.wrapInHtml(message);
  }

  private @Nullable PyFunction getFunction() {
    return (PyFunction)getStartElement();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PyFunction function = getFunction();
    final PyMethodDescriptor descriptor = createMethodDescriptor(function);

    final PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, descriptor) {
      // Similar to JavaChangeSignatureDialog.createAndPreselectNew()
      @Override
      protected int getSelectedIdx() {
        return (int)StreamEx.of(getParameters()).indexOf(info -> info.getOldIndex() < 0).orElse(super.getSelectedIdx());
      }
    };

    final PyCallSiteExpression originalCallSite = myOriginalCallSiteExpression != null ? myOriginalCallSiteExpression.getElement() : null;
    try {
      if (originalCallSite != null) {
        originalCallSite.putUserData(CHANGE_SIGNATURE_ORIGINAL_CALL, true);
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        BaseRefactoringProcessor processor = dialog.createRefactoringProcessor();
        dialog.close(DialogWrapper.OK_EXIT_CODE);
        processor.run();
      }
      else {
        dialog.show();
      }
    }
    finally {
      if (originalCallSite != null) {
        originalCallSite.putUserData(CHANGE_SIGNATURE_ORIGINAL_CALL, null);
      }
    }
  }

  private static @NotNull String generateParameterName(@NotNull PyExpression argumentValue,
                                                       @NotNull PyFunction function,
                                                       @NotNull Set<String> usedParameterNames,
                                                       @NotNull TypeEvalContext context) {
    final Collection<String> suggestions = new LinkedHashSet<>();
    final PyCallExpression callExpr = as(argumentValue, PyCallExpression.class);
    final PyElement referenceElem = as(callExpr != null ? callExpr.getCallee() : argumentValue, PyReferenceExpression.class);
    if (referenceElem != null) {
      suggestions.addAll(NameSuggesterUtil.generateNames(referenceElem.getText()));
    }
    if (suggestions.isEmpty()) {
      PyType type = context.getType(argumentValue);
      if (type instanceof PyUnionType unionType) {
        type = ContainerUtil.findInstance(unionType.getMembers(), PyClassType.class);
      }
      final String typeName = type != null && type.getName() != null ? type.getName() : "object";
      suggestions.addAll(NameSuggesterUtil.generateNamesByType(typeName));
    }
    final String shortestName = Collections.min(suggestions, Comparator.comparingInt(String::length));

    String result = shortestName;
    int counter = 1;
    while (!PyRefactoringUtil.isValidNewName(result, function.getStatementList()) || usedParameterNames.contains(result)) {
      result = shortestName + counter;
      counter++;
    }
    return result;
  }

  private @NotNull PyMethodDescriptor createMethodDescriptor(final PyFunction function) {
    return new PyMethodDescriptor(function) {
      private final List<Pair<Integer, PyParameterInfo>> myExtraParameters = myExtraParametersSupplier.get();

      @Override
      public @NotNull List<PyParameterInfo> getParameters() {
        final List<PyParameterInfo> result = new ArrayList<>();
        final List<PyParameterInfo> originalParams = super.getParameters();
        final PeekingIterator<Pair<Integer, PyParameterInfo>> extra = Iterators.peekingIterator(myExtraParameters.iterator());
        while (extra.hasNext() && extra.peek().getFirst() < 0) {
          result.add(extra.next().getSecond());
        }
        for (int i = 0; i < originalParams.size(); i++) {
          result.add(originalParams.get(i));
          while (extra.hasNext() && extra.peek().getFirst() == i) {
            result.add(extra.next().getSecond());
          }
        }
        return result;
      }
    };
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return getFunction();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
