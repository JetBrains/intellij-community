// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.*;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.documentation.PyDocSignaturesHighlighterKt.*;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Provides quick docs for classes, methods, and functions.
 * Generates documentation stub
 */
public class PythonDocumentationProvider implements DocumentationProvider {
  public static final String DOCUMENTATION_CONFIGURABLE_ID = "com.jetbrains.python.documentation.PythonDocumentationConfigurable";

  private static final int RETURN_TYPE_WRAPPING_THRESHOLD = 80;

  // provides ctrl+hover info
  @Override
  public @Nullable @Nls String getQuickNavigateInfo(PsiElement element, @NotNull PsiElement originalElement) {
    final PsiElement referenceElement = originalElement.getParent(); // identifier -> expression
    for (PythonDocumentationQuickInfoProvider point : PythonDocumentationQuickInfoProvider.EP_NAME.getExtensions()) {
      final String info = point.getQuickInfo(referenceElement);
      if (info != null) {
        return info;
      }
    }

    final TypeEvalContext context = TypeEvalContext.userInitiated(referenceElement.getProject(), referenceElement.getContainingFile());

    if (element instanceof PyFunction function) {
      final HtmlBuilder result = new HtmlBuilder();

      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        final String clsName = cls.getName();
        if (clsName != null) {
          result.append(styledSpan(PyPsiBundle.message("QDOC.class"), PyHighlighter.PY_KEYWORD));
          result.nbsp();
          result.append(styledSpan(clsName, PyHighlighter.PY_CLASS_DEFINITION));
          result.br();
          // It would be nice to have class import info here, but we don't know the ctrl+hovered reference and context
        }
      }

      return result
        .append(describeDecorators(function, HtmlChunk.text(", ")))
        .append(describeFunction(function, context, true))
        .toString();
    }
    else if (element instanceof PyClass cls) {
      final HtmlBuilder result = new HtmlBuilder();
      return result
        .append(describeDecorators(cls, HtmlChunk.text(", ")))
        .append(describeClass(cls, context))
        .toString();
    }
    else if (element instanceof PsiDirectory directory) {
      return PyPsiBundle.message("QDOC.directory.name", directory.getName());
    }
    else if (element instanceof PsiFile file) {
      return PyPsiBundle.message("QDOC.file.name", file.getName());
    }
    else if (element instanceof PyExpression) {
      return describeExpression((PyExpression)element, referenceElement, context);
    }
    else if (element instanceof PyTypeParameter typeParameter) {
      return PyPsiBundle.message("QDOC.type.parameter.name", describeTypeParameter(typeParameter, true, context));
    }
    else if (element instanceof PyTypeAliasStatement typeAliasStatement) {
      return describeTypeAlias(typeAliasStatement, context).toString();
    }
    return null;
  }

  static @NotNull HtmlChunk describeFunction(@NotNull PyFunction function,
                                             @NotNull TypeEvalContext context,
                                             boolean forTooltip) {
    return HtmlChunk.raw(describeFunctionWithTypes(function, context, forTooltip));
  }

  static @NotNull HtmlChunk describeTarget(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    final HtmlBuilder result = new HtmlBuilder();
    result.append(styledSpan(StringUtil.notNullize(target.getName()), DefaultLanguageHighlighterColors.IDENTIFIER));
    result.append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN));
    result.append(styledSpan(formatTypeWithLinks(context.getType(target), target, target, context), PyHighlighter.PY_ANNOTATION));

    // Can return not physical elements such as foo()[0] for assignments like x, _ = foo()
    final PyExpression value = target.findAssignedValue();
    if (value != null) {
      result.append(styledSpan(" = ", PyHighlighter.PY_OPERATION_SIGN));
      final String initializerText = value.getText();
      final int index = value.getText().indexOf("\n");
      if (index < 0) {
        result.append(highlightExpressionText(initializerText, value));
      }
      else {
        result.append(highlightExpressionText(initializerText.substring(0, index), value));
        result.append(styledSpan("...", PyHighlighter.PY_DOT));
      }
    }
    return result.toFragment();
  }

  static @NotNull HtmlChunk describeParameter(@NotNull PyNamedParameter parameter, @NotNull TypeEvalContext context) {
    final HtmlBuilder result = new HtmlBuilder();
    result.append(styledSpan(StringUtil.notNullize(parameter.getName()), paramNameTextAttribute(parameter.isSelf())));
    result.append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN));
    result.append(styledSpan(formatTypeWithLinks(context.getType(parameter), parameter, parameter, context), PyHighlighter.PY_ANNOTATION));
    return result.toFragment();
  }

  static @NotNull HtmlChunk describeTypeParameter(@NotNull PyTypeParameter typeParameter, boolean showKind, @NotNull TypeEvalContext context) {
    HtmlBuilder result = new HtmlBuilder();
    result.append(styledSpan(StringUtil.notNullize(typeParameter.getName()), PyHighlighter.PY_TYPE_PARAMETER));
    PyExpression boundExpression = typeParameter.getBoundExpression();
    if (boundExpression != null && typeParameter.getBoundExpressionText() != null) {
      result.append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN));
      result.append(highlightExpressionText(typeParameter.getBoundExpressionText(), typeParameter.getBoundExpression()));
    }
    if (showKind) {
      result
        .append(", ")
        .append(PyPsiBundle.message("QDOC.type.parameter.kind"))
        .append(" ")
        .append(styledSpan(formatTypeWithLinks(context.getType(typeParameter), typeParameter, typeParameter, context),
                           PyHighlighter.PY_ANNOTATION));
    }
    return result.toFragment();
  }


  static @NotNull HtmlChunk describeTypeAlias(@NotNull PyTypeAliasStatement typeAliasStatement, @NotNull TypeEvalContext context) {
    HtmlBuilder result = new HtmlBuilder();
    result.append(styledSpan("type ", PyHighlighter.PY_KEYWORD)); //NON-NLS
    result.append(styledSpan(StringUtil.notNullize(typeAliasStatement.getName()), DefaultLanguageHighlighterColors.IDENTIFIER));
    if (typeAliasStatement.getTypeParameterList() != null) {
      List<PyTypeParameter> typeParameters = typeAliasStatement.getTypeParameterList().getTypeParameters();
      result.append(styledSpan("[", PyHighlighter.PY_BRACKETS));
      result.append(StreamEx
                      .of(typeParameters)
                      .map(typeParameter -> describeTypeParameter(typeParameter, false, context))
                      .collect(HtmlChunk.toFragment(styledSpan(", ", PyHighlighter.PY_COMMA))));
      result.append(styledSpan("]", PyHighlighter.PY_BRACKETS));
    }
    PyExpression typeExpression = typeAliasStatement.getTypeExpression();
    if (typeExpression != null) {
      result.append(styledSpan(" = ", PyHighlighter.PY_OPERATION_SIGN));
      result.append(styledSpan(formatTypeWithLinks(context.getType(typeExpression),
                                                   typeExpression, typeAliasStatement, context), PyHighlighter.PY_ANNOTATION));
    }
    return result.toFragment();
  }

  private static @NlsSafe @NotNull String describeFunctionWithTypes(@NotNull PyFunction function,
                                                                    @NotNull TypeEvalContext context,
                                                                    boolean forTooltip) {
    final StringBuilder result = new StringBuilder();
    int firstParamOffset = 0;
    // TODO wrapping of long signatures

    if (function.isAsync()) {
      result.append(styledSpan("async ", PyHighlighter.PY_KEYWORD)); //NON-NLS
      firstParamOffset += "async ".length();
    }
    result.append(styledSpan("def ", PyHighlighter.PY_KEYWORD)); //NON-NLS
    firstParamOffset += "def ".length();

    final String funcName = StringUtil.notNullize(function.getName(), PyNames.UNNAMED_ELEMENT);

    firstParamOffset += funcName.length();
    result.append(styledSpan(funcName, functionNameTextAttribute(function, funcName)));

    result.append(styledSpan("(", PyHighlighter.PY_PARENTHS));
    firstParamOffset++;

    int lastLineOffset = 0;
    boolean first = true;
    boolean firstIsSelf = false;
    final List<PyCallableParameter> parameters = function.getParameters(context);
    for (PyCallableParameter parameter : parameters) {
      boolean isSelf = parameter.isSelf();
      if (!first) {
        result.append(styledSpan(",", PyHighlighter.PY_COMMA));
        if (forTooltip || firstIsSelf && parameters.size() == 2) {
          result.append(" ");
        }
        else {
          result.append("\n");
          lastLineOffset = result.length();
          // alignment
          StringUtil.repeatSymbol(result, ' ', firstParamOffset);
        }
      }
      else {
        firstIsSelf = isSelf;
      }

      String paramName = parameter.getName();
      PyType paramType = parameter.getType(context);
      final PyNamedParameter named = as(parameter.getParameter(), PyNamedParameter.class);
      boolean showType = true;
      if (parameter.isPositionalContainer()) {
        paramName = "*" + StringUtil.notNullize(paramName, "args"); //NON-NLS
        final PyTupleType tupleType = as(paramType, PyTupleType.class);
        if (tupleType != null) {
          paramType = tupleType.getIteratedItemType();
        }
      }
      else if (parameter.isKeywordContainer()) {
        paramName = "**" + StringUtil.notNullize(paramName, "kwargs"); //NON-NLS
        final PyCollectionType genericType = as(paramType, PyCollectionType.class);
        if (genericType != null && genericType.getPyClass() == PyBuiltinCache.getInstance(function).getClass("dict")) {
          final List<PyType> typeParams = genericType.getElementTypes();
          paramType = typeParams.size() == 2 ? typeParams.get(1) : null;
        }
      }
      else if (parameter.getParameter() instanceof PySlashParameter) {
        paramName = PyAstSlashParameter.TEXT;
        showType = false;
      }
      else if (parameter.getParameter() instanceof PySingleStarParameter) {
        paramName = PyAstSingleStarParameter.TEXT;
        showType = false;
      }
      else {
        paramName = StringUtil.notNullize(paramName, PyNames.UNNAMED_ELEMENT);
        // Don't show type for "self" unless it's explicitly annotated
        showType = !isSelf || (named != null && new PyTypingTypeProvider().getParameterType(named, function, context) != null);
      }

      result.append(styledSpan(paramName, paramNameTextAttribute(isSelf)));
      if (showType) {
        result
          .append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN))
          .append(styledSpan(formatTypeWithLinks(paramType, named, function, context), PyHighlighter.PY_ANNOTATION));
      }

      final String signature = ParamHelper.getDefaultValuePartInSignature(parameter.getDefaultValueText(), showType);
      if (signature != null) {
        @SuppressWarnings("RegExpRepeatedSpace") final String delimiter = showType ? " = " : "=";
        final String[] parts = signature.split(delimiter);
        if (parts.length == 2) {
          result.append(styledSpan(delimiter, PyHighlighter.PY_OPERATION_SIGN));
          result.append(highlightExpressionText(parts[1], parameter.getDefaultValue()));
        }
      }
      first = false;
    }

    result.append(styledSpan(")", PyHighlighter.PY_PARENTHS));

    if (!forTooltip && StringUtil.stripHtml(result.substring(lastLineOffset), false).length() > RETURN_TYPE_WRAPPING_THRESHOLD) {
      result.append("\n ");
    }
    final PyType returnType = context.getReturnType(function);
    result.append(HtmlChunk.text(" -> "));
    result.append(styledSpan(formatTypeWithLinks(returnType, function, function, context), PyHighlighter.PY_ANNOTATION));
    return result.toString();
  }

  private static @Nullable @Nls String describeExpression(@NotNull PyExpression expression,
                                                          @NotNull PsiElement originalElement,
                                                          @NotNull TypeEvalContext context) {
    final String name = expression.getName();
    if (name != null) {
      final HtmlBuilder result = new HtmlBuilder();
      if (expression instanceof PyNamedParameter) {
        final PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
        if (function != null) {
          final String functionName = function.getName();
          if (function.getContainingClass() == null) {
            result.append(PyPsiBundle.message("QDOC.parameter.of.function.name", name, functionName));
          }
          else {
            result.append(PyPsiBundle.message("QDOC.parameter.of.method.name", name, functionName));
          }
        }
        else {
          result.append(PyPsiBundle.message("QDOC.parameter.name", name));
        }
      }
      else {
        result.append(PyPsiBundle.message("QDOC.variable.name", name));
      }

      if (originalElement instanceof PyTypedElement typedElement) {
        final PyType type = context.getType(typedElement);
        final HtmlChunk formattedType = formatTypeWithLinks(type, typedElement, typedElement, context);
        result.br().appendRaw(PyPsiBundle.message("QDOC.inferred.type.name", formattedType));
      }
      return result.toString();
    }
    return null;
  }

  /**
   * @param type    type which name will be calculated
   * @param context type evaluation context
   * @return string representation of the type
   */
  public static @NotNull @NlsSafe String getTypeName(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return PyTypeVisitor.visit(type, new PyTypeRenderer.Documentation(context)).toString();
  }

  /**
   * Returns the provided type in PEP 484 compliant format.
   */
  public static @NotNull String getTypeHint(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return PyTypeVisitor.visit(type, new PyTypeRenderer.TypeHint(context)).toString();
  }

  /**
   * Provides additional information about the type
   *
   * @param type    type which name will be calculated
   * @param context type evaluation context
   * @return string representation of the type similar to {@link #getTypeName(PyType, TypeEvalContext)}, but with additional information,
   * such as bounds for TypeVar types in ' ≤: *bound*' format
   */
  public static @NotNull String getVerboseTypeName(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return PyTypeVisitor.visit(type, new PyTypeRenderer.VerboseDocumentation(context)).toString();
  }

  /**
   * @param type      type which description will be calculated.
   *                  Description is the same as {@link PythonDocumentationProvider#getTypeName(PyType, TypeEvalContext)} gives but
   *                  types are converted to links.
   * @param typeOwner element that has the given type, can be {@code null} for synthetic parameters
   * @param context   type evaluation context
   * @param anchor    anchor element
   * @param body      body to be used to append description
   */
  public static void describeTypeWithLinks(@Nullable PyType type,
                                           @Nullable PyTypedElement typeOwner,
                                           @NotNull TypeEvalContext context,
                                           @NotNull PsiElement anchor,
                                           @NotNull HtmlBuilder body) {
    // Variable annotated with "typing.TypeAlias" marker is deliberately treated as having "Any" type
    if (typeOwner instanceof PyTargetExpression && type == null) {
      PyAssignmentStatement assignment = as(typeOwner.getParent(), PyAssignmentStatement.class);
      if (assignment != null && PyTypingTypeProvider.isExplicitTypeAlias(assignment, context)) {
        body.append(styledSpan("TypeAlias", PyHighlighter.PY_ANNOTATION));
        return;
      }
    }
    body.append(PyTypeVisitor.visit(type, new PyTypeRenderer.RichDocumentation(context, anchor)));
  }

  static @NotNull HtmlChunk describeDecorators(@NotNull PyDecoratable decoratable,
                                               @NotNull HtmlChunk separator) {
    final HtmlBuilder result = new HtmlBuilder();
    final PyDecoratorList decoratorList = decoratable.getDecoratorList();
    if (decoratorList != null) {
      boolean first = true;

      for (PyDecorator decorator : decoratorList.getDecorators()) {
        if (!first) {
          result.append(separator);
        }
        result.appendRaw(describeDecorator(decorator).toString());
        first = false;
      }
    }
    if (!result.isEmpty()) {
      result.br();
    }
    return result.toFragment();
  }

  static @NotNull HtmlChunk describeClass(@NotNull PyClass cls,
                                          @NotNull TypeEvalContext context) {
    final HtmlBuilder result = new HtmlBuilder();
    final @NlsSafe String name = StringUtil.notNullize(cls.getName(), PyNames.UNNAMED_ELEMENT);
    result.append(styledSpan("class ", PyHighlighter.PY_KEYWORD)); //NON-NLS
    result.append(styledSpan(name, PyHighlighter.PY_CLASS_DEFINITION));

    final PyExpression[] superClasses = cls.getSuperClassExpressions();
    if (superClasses.length > 0) {
      result.append(styledSpan("(", PyHighlighter.PY_PARENTHS));
      boolean isNotFirst = false;

      for (PyExpression superClass : superClasses) {
        if (isNotFirst) {
          result.append(styledSpan(", ", PyHighlighter.PY_COMMA));
        }
        else {
          isNotFirst = true;
        }
        result.append(describeSuperClass(superClass, context));
      }
      result.append(styledSpan(")", PyHighlighter.PY_PARENTHS));
    }

    return result.toFragment();
  }

  private static @NotNull HtmlChunk describeSuperClass(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final @NlsSafe String expressionText = expression.getText();
    if (expression instanceof PyReferenceExpression referenceExpression) {
      if (!referenceExpression.isQualified()) {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        for (ResolveResult result : referenceExpression.getReference(resolveContext).multiResolve(false)) {
          if (result.getElement() instanceof PyClass pyClass && pyClass.getQualifiedName() != null) {
            return styledReference(PyDocumentationLink.toClass(pyClass, expressionText), pyClass);
          }
        }
      }
    }
    else if (expression instanceof PySubscriptionExpression) {
      final PyExpression operand = ((PySubscriptionExpression)expression).getOperand();
      final PyExpression indexExpression = ((PySubscriptionExpression)expression).getIndexExpression();

      if (indexExpression != null) {
        return new HtmlBuilder()
          .append(describeSuperClass(operand, context))
          .append(styledSpan("[", PyHighlighter.PY_BRACKETS))
          .append(describeSuperClass(indexExpression, context))
          .append(styledSpan("]", PyHighlighter.PY_BRACKETS))
          .toFragment();
      }
    }
    else if (expression instanceof PyKeywordArgument) {
      final String keyword = ((PyKeywordArgument)expression).getKeyword();
      final PyExpression valueExpression = ((PyKeywordArgument)expression).getValueExpression();

      if (PyNames.METACLASS.equals(keyword) && valueExpression != null) {
        return new HtmlBuilder()
          .append(styledSpan(PyNames.METACLASS, PyHighlighter.PY_KEYWORD_ARGUMENT))
          .append(styledSpan("=", PyHighlighter.PY_OPERATION_SIGN))
          .append(describeSuperClass(valueExpression, context))
          .toFragment();
      }
    }
    else if (PyClassImpl.isSixWithMetaclassCall(expression)) {
      final PyCallExpression callExpression = (PyCallExpression)expression;
      final PyExpression callee = callExpression.getCallee();

      if (callee != null) {
        return new HtmlBuilder()
          .append(styledSpan(callee.getText(), DefaultLanguageHighlighterColors.IDENTIFIER))
          .append(styledSpan("(", PyHighlighter.PY_PARENTHS))
          .append(StreamEx
                    .of(callExpression.getArguments())
                    .map(argument -> describeSuperClass(argument, context))
                    .collect(HtmlChunk.toFragment(styledSpan(", ", PyHighlighter.PY_COMMA))))
          .append(styledSpan(")", PyHighlighter.PY_PARENTHS))
          .toFragment();
      }
    }
    return HtmlChunk.text(expressionText);
  }

  private static @NotNull HtmlChunk describeDecorator(@NotNull PyDecorator decorator) {
    final HtmlBuilder result = new HtmlBuilder();
    result.append(styledSpan("@" + PyUtil.getReadableRepr(decorator.getCallee(), false), PyHighlighter.PY_DECORATOR));

    final PyArgumentList argumentList = decorator.getArgumentList();
    if (argumentList != null) {
      result.append(styledSpan("(", PyHighlighter.PY_PARENTHS));
      final PyExpression[] argumentsExpressions = argumentList.getArguments();

      final HtmlChunk styledArgumentsList =
        StreamEx
          .of(argumentsExpressions)
          .map(argExpression -> {
            if (!(argExpression instanceof PyKeywordArgument keywordArg)) {
              return highlightExpressionText(argExpression.getText(), argExpression);
            }
            final String argName = argExpression.getName();
            if (argName == null) return null;
            final PyExpression argValueExpression = keywordArg.getValueExpression();
            if (argValueExpression == null) return null;
            return new HtmlBuilder().append(styledSpan(argName, PyHighlighter.PY_KEYWORD_ARGUMENT))
              .append(styledSpan("=", PyHighlighter.PY_OPERATION_SIGN))
              .append(highlightExpressionText(argValueExpression.getText(), argValueExpression))
              .toFragment();
          })
          .nonNull()
          .collect(HtmlChunk.toFragment(styledSpan(", ", PyHighlighter.PY_COMMA)));

      result.append(styledArgumentsList);
      result.append(styledSpan(")", PyHighlighter.PY_PARENTHS));
    }
    return result.toFragment();
  }

  // provides ctrl+Q doc
  @Override
  public @Nls String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    final PythonRuntimeService runtimeService = PythonRuntimeService.getInstance();
    if (runtimeService.isInPydevConsole(element) || originalElement != null && runtimeService.isInPydevConsole(originalElement)) {
      return runtimeService.createPydevDoc(element, originalElement);
    }
    return new PyDocumentationBuilder(element, originalElement).build();
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, @NotNull String link, @NotNull PsiElement context) {
    return PyDocumentationLink.elementForLink(link,
                                              context,
                                              TypeEvalContext.userInitiated(context.getProject(), context.getContainingFile()));
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    final String url = getOnlyUrlFor(element, originalElement);
    return url == null ? null : Collections.singletonList(url);
  }

  @Override
  public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                            @NotNull PsiFile file,
                                                            @Nullable PsiElement contextElement,
                                                            int targetOffset) {
    if (contextElement != null) {
      final IElementType elementType = contextElement.getNode().getElementType();
      if (PythonDialectsTokenSetProvider.getInstance().getKeywordTokens().contains(elementType)) {
        return contextElement;
      }
      final PsiElement parent = contextElement.getParent();
      if (parent instanceof PyArgumentList && (PyTokenTypes.LPAR == elementType || PyTokenTypes.RPAR == elementType)) {
        final PyCallExpression expression = PsiTreeUtil.getParentOfType(contextElement, PyCallExpression.class);
        if (expression != null) {
          final PyExpression callee = expression.getCallee();
          if (callee != null) {
            final PsiReference reference = callee.getReference();
            if (reference != null) {
              return reference.resolve();
            }
          }
        }
      }
      final PyExpression expression = PsiTreeUtil.getParentOfType(contextElement, PyExpression.class);
      if (expression != null && DocStringUtil.isDocStringExpression(expression)) {
        final PyDocStringOwner docstringOwner = PsiTreeUtil.getParentOfType(contextElement, PyDocStringOwner.class);
        if (docstringOwner != null) return docstringOwner;
      }
    }
    return null;
  }

  private static @NotNull HtmlChunk formatTypeWithLinks(@Nullable PyType type,
                                                        @Nullable PyTypedElement typeOwner,
                                                        @NotNull PsiElement anchor,
                                                        @NotNull TypeEvalContext context) {
    final HtmlBuilder builder = new HtmlBuilder();
    describeTypeWithLinks(type, typeOwner, context, anchor, builder);
    return builder.toFragment();
  }

  public static @Nullable QualifiedName getFullQualifiedName(final @Nullable PsiElement element) {
    final String name =
      (element instanceof PsiNamedElement) ? ((PsiNamedElement)element).getName() : element != null ? element.getText() : null;
    if (name != null) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
      if (owner instanceof PyClass) {
        final QualifiedName importQName = QualifiedNameFinder.findCanonicalImportPath(element, element);
        if (importQName != null) {
          return QualifiedName.fromDottedString(importQName + "." + owner.getName() + "." + name);
        }
      }
      else if (PyUtil.isInitOrNewMethod(owner)) {
        final QualifiedName importQName = QualifiedNameFinder.findCanonicalImportPath(owner, element);
        final PyClass containingClass = ((PyFunction)owner).getContainingClass();
        if (importQName != null && containingClass != null) {
          return QualifiedName.fromDottedString(importQName + "." + containingClass.getName() + "." + name);
        }
      }
      else if (owner instanceof PyFile) {
        if (builtinCache.isBuiltin(element)) {
          return QualifiedName.fromDottedString(name);
        }
        else {
          final VirtualFile virtualFile = ((PyFile)owner).getVirtualFile();
          if (virtualFile != null) {
            final QualifiedName fileQName = QualifiedNameFinder.findCanonicalImportPath(element, element);
            if (fileQName != null) {
              return QualifiedName.fromDottedString(fileQName + "." + name);
            }
          }
        }
      }
      else {
        if (element instanceof PyFile) {
          return QualifiedNameFinder.findCanonicalImportPath(element, element);
        }
      }
    }
    return null;
  }

  protected static @Nullable PsiFileSystemItem getFile(PsiElement element) {
    PsiFileSystemItem file = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
    return (PsiFileSystemItem)PyUtil.turnInitIntoDir(file);
  }

  public static @Nullable PsiNamedElement getNamedElement(@Nullable PsiElement element) {
    PsiNamedElement namedElement = (element instanceof PsiNamedElement) ? (PsiNamedElement)element : null;
    final PyClass containingClass = PyUtil.turnConstructorIntoClass(as(namedElement, PyFunction.class));
    if (containingClass != null) {
      namedElement = containingClass;
    }
    else {
      namedElement = (PsiNamedElement)PyUtil.turnInitIntoDir(namedElement);
    }
    return namedElement;
  }

  public static @Nullable String getOnlyUrlFor(PsiElement element, PsiElement originalElement) {
    PsiFileSystemItem file = getFile(element);
    if (file == null) return null;
    final Sdk sdk = PyBuiltinCache.findSdkForFile(file);
    if (sdk == null) {
      return null;
    }
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, originalElement);
    if (qName == null) {
      return null;
    }
    final PythonDocumentationMap map = PythonDocumentationMap.getInstance();
    final String pyVersion = pyVersion(sdk.getVersionString());
    PsiNamedElement namedElement = getNamedElement(element);
    final String url = map.urlFor(qName, namedElement, pyVersion);
    if (url != null) {
      return url;
    }
    for (PythonDocumentationLinkProvider provider : PythonDocumentationLinkProvider.EP_NAME.getExtensionList()) {
      final String providerUrl = provider.getExternalDocumentationUrl(element, originalElement);
      if (providerUrl != null) {
        return providerUrl;
      }
    }
    return null;
  }

  public static @Nullable String pyVersion(@Nullable String versionString) {
    final String prefix = "Python ";
    if (versionString != null && versionString.startsWith(prefix)) {
      final String version = versionString.substring(prefix.length());
      int dot = version.indexOf('.');
      if (dot > 0) {
        dot = version.indexOf('.', dot + 1);
        if (dot > 0) {
          return version.substring(0, dot);
        }
        return version;
      }
    }
    return null;
  }
}
