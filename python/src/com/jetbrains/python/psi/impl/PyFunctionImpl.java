// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.interpretAsModifierWrappingCall;

/**
 * Implements PyFunction.
 */
public class PyFunctionImpl extends PyBaseElementImpl<PyFunctionStub> implements PyFunction {

  private static final Key<CachedValue<List<PyAssignmentStatement>>>
    ATTRIBUTES_KEY = Key.create("attributes");

  public PyFunctionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFunctionImpl(final PyFunctionStub stub) {
    this(stub, PyElementTypes.FUNCTION_DECLARATION);
  }

  public PyFunctionImpl(PyFunctionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  private class CachedStructuredDocStringProvider implements CachedValueProvider<StructuredDocString> {
    @Nullable
    @Override
    public Result<StructuredDocString> compute() {
      final PyFunctionImpl f = PyFunctionImpl.this;
      return Result.create(DocStringUtil.getStructuredDocString(f), f);
    }
  }

  @NotNull private final CachedStructuredDocStringProvider myCachedStructuredDocStringProvider = new CachedStructuredDocStringProvider();
  @Nullable private volatile Boolean myIsGenerator;

  @Nullable
  @Override
  public String getName() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }

    ASTNode node = getNameNode();
    return node != null ? node.getText() : null;
  }

  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = PyUtil.createNewName(this, name);
    final ASTNode nameNode = getNameNode();
    if (nameNode != null) {
      getNode().replaceChild(nameNode, nameElement);
    }
    return this;
  }

  @Override
  public Icon getIcon(int flags) {
    PyPsiUtils.assertValid(this);
    final Property property = getProperty();
    if (property != null) {
      if (property.getGetter().valueOrNull() == this) {
        return PythonIcons.Python.PropertyGetter;
      }
      if (property.getSetter().valueOrNull() == this) {
        return PythonIcons.Python.PropertySetter;
      }
      if (property.getDeleter().valueOrNull() == this) {
        return PythonIcons.Python.PropertyDeleter;
      }
      return PlatformIcons.PROPERTY_ICON;
    }
    if (getContainingClass() != null) {
      return PlatformIcons.METHOD_ICON;
    }
    return PythonIcons.Python.Function;
  }

  @Override
  @Nullable
  public ASTNode getNameNode() {
    ASTNode id = getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    if (id == null) {
      ASTNode error = getNode().findChildByType(TokenType.ERROR_ELEMENT);
      if (error != null) {
        id = error.findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens());
      }
    }
    return id;
  }

  @Override
  @NotNull
  public PyParameterList getParameterList() {
    return getRequiredStubOrPsiChild(PyElementTypes.PARAMETER_LIST);
  }

  @NotNull
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return Optional
      .ofNullable(context.getType(this))
      .filter(PyCallableType.class::isInstance)
      .map(PyCallableType.class::cast)
      .map(callableType -> callableType.getParameters(context))
      .orElseGet(() -> ContainerUtil.map(getParameterList().getParameters(), PyCallableParameterImpl::psi));
  }

  @Override
  @NotNull
  public PyStatementList getStatementList() {
    final PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for function " + getText();
    return statementList;
  }

  @Override
  @Nullable
  public PyClass getContainingClass() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof PyClassStub) {
        return ((PyClassStub)parentStub).getPsi();
      }

      return null;
    }

    final PsiElement parent = PsiTreeUtil.getParentOfType(this, StubBasedPsiElement.class);
    if (parent instanceof PyClass) {
      return (PyClass)parent;
    }
    return null;
  }

  @Override
  @Nullable
  public PyDecoratorList getDecoratorList() {
    return getStubOrPsiChild(PyElementTypes.DECORATOR_LIST); // PsiTreeUtil.getChildOfType(this, PyDecoratorList.class);
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final Ref<PyType> returnTypeRef = typeProvider.getReturnType(this, context);
      if (returnTypeRef != null) {
        return derefType(returnTypeRef, typeProvider);
      }
    }

    PyType inferredType = null;
    if (context.allowReturnTypes(this)) {
      final Ref<? extends PyType> yieldTypeRef = getYieldStatementType(context);
      if (yieldTypeRef != null) {
        inferredType = yieldTypeRef.get();
      }
      else {
        inferredType = getReturnStatementType(context);
      }
    }

    return PyTypingTypeProvider.toAsyncIfNeeded(this, inferredType);
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final Ref<PyType> typeRef = typeProvider.getCallType(this, callSite, context);
      if (typeRef != null) {
        return derefType(typeRef, typeProvider);
      }
    }

    final PyExpression receiver = callSite.getReceiver(this);
    final PyCallExpression.PyArgumentsMapping fullMapping = PyCallExpressionHelper.mapArguments(callSite, this, context);
    final Map<PyExpression, PyCallableParameter> mappedExplicitParameters = fullMapping.getMappedParameters();

    final Map<PyExpression, PyCallableParameter> allMappedParameters = new LinkedHashMap<>();
    final PyCallableParameter firstImplicit = ContainerUtil.getFirstItem(fullMapping.getImplicitParameters());
    if (receiver != null && firstImplicit != null) {
      allMappedParameters.put(receiver, firstImplicit);
    }
    allMappedParameters.putAll(mappedExplicitParameters);

    return getCallType(receiver, allMappedParameters, context);
  }

  @Nullable
  private static PyType derefType(@NotNull Ref<PyType> typeRef, @NotNull PyTypeProvider typeProvider) {
    final PyType type = typeRef.get();
    if (type != null) {
      type.assertValid(typeProvider.toString());
    }
    return type;
  }

  @Nullable
  @Override
  public PyType getCallType(@Nullable PyExpression receiver,
                            @NotNull Map<PyExpression, PyCallableParameter> parameters,
                            @NotNull TypeEvalContext context) {
    return analyzeCallType(PyUtil.getReturnTypeToAnalyzeAsCallType(this, context), receiver, parameters, context);
  }

  @Nullable
  private PyType analyzeCallType(@Nullable PyType type,
                                 @Nullable PyExpression receiver,
                                 @NotNull Map<PyExpression, PyCallableParameter> parameters,
                                 @NotNull TypeEvalContext context) {
    if (PyTypeChecker.hasGenerics(type, context)) {
      final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(receiver, parameters, context);
      if (substitutions != null) {
        type = PyTypeChecker.substitute(type, substitutions, context);
      }
      else {
        type = null;
      }
    }
    if (receiver != null) {
      type = replaceSelf(type, receiver, context);
    }
    if (type != null && isDynamicallyEvaluated(parameters.values(), context)) {
      type = PyUnionType.createWeakType(type);
    }
    return type;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this) {
      @NotNull
      @Override
      public String getPresentableText() {
        return notNullize(getName(), PyNames.UNNAMED_ELEMENT) + getParameterList().getPresentableText(true);
      }

      @Nullable
      @Override
      public String getLocationString() {
        final PyClass containingClass = getContainingClass();
        if (containingClass != null) {
          return "(" + containingClass.getName() + " in " + getPackageForFile(getContainingFile()) + ")";
        }
        return super.getLocationString();
      }
    };
  }

  @Nullable
  private PyType replaceSelf(@Nullable PyType returnType, @Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    return replaceSelf(returnType, receiver, context, true);
  }

  @Nullable
  private PyType replaceSelf(@Nullable PyType returnType,
                             @Nullable PyExpression receiver,
                             @NotNull TypeEvalContext context,
                             boolean allowCoroutineOrGenerator) {
    if (receiver != null) {
      // TODO: Currently we substitute only simple subclass types and unions, but we could handle collection types as well
      if (returnType instanceof PyClassType) {
        final PyClassType returnClassType = (PyClassType)returnType;

        if (returnClassType.getPyClass() == getContainingClass()) {
          final PyType receiverType = context.getType(receiver);

          if (receiverType instanceof PyClassType) {
            final PyClassType receiverClassType = (PyClassType)receiverType;

            if (receiverClassType.getPyClass() != returnClassType.getPyClass() &&
                PyTypeChecker.match(returnClassType.toClass(), receiverClassType.toClass(), context)) {
              return returnClassType.isDefinition() ? receiverClassType.toClass() : receiverClassType.toInstance();
            }
          }
        }
        else if (allowCoroutineOrGenerator &&
                 returnType instanceof PyCollectionType &&
                 PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType) != null) {
          final List<PyType> replacedElementTypes = ContainerUtil.map(
            ((PyCollectionType)returnType).getElementTypes(),
            type -> replaceSelf(type, receiver, context, false)
          );

          return new PyCollectionTypeImpl(returnClassType.getPyClass(),
                                          returnClassType.isDefinition(),
                                          replacedElementTypes);
        }
      }
      else if (returnType instanceof PyUnionType) {
        final Collection<PyType> members = ((PyUnionType)returnType).getMembers();
        return PyUnionType.union(ContainerUtil.map(members, type -> replaceSelf(type, receiver, context, true)));
      }
    }
    return returnType;
  }

  private static boolean isDynamicallyEvaluated(@NotNull Collection<PyCallableParameter> parameters, @NotNull TypeEvalContext context) {
    for (PyCallableParameter parameter : parameters) {
      final PyType type = parameter.getType(context);
      if (type instanceof PyDynamicallyEvaluatedType) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private Ref<? extends PyType> getYieldStatementType(@NotNull final TypeEvalContext context) {
    Ref<PyType> elementType = null;
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
    final PyStatementList statements = getStatementList();
    final Set<PyType> types = new LinkedHashSet<>();
    statements.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyYieldExpression(PyYieldExpression node) {
        final PyExpression expr = node.getExpression();
        final PyType type = expr != null ? context.getType(expr) : null;

        if (node.isDelegating()) {
          if (type instanceof PyCollectionType) {
            types.add(((PyCollectionType)type).getIteratedItemType());
          }
          else if (ArrayUtil.contains(type, cache.getListType(), cache.getDictType(), cache.getSetType(), cache.getTupleType())) {
            types.add(null);
          }
          else {
            types.add(type);
          }
        }
        else {
          types.add(type);
        }
      }

      @Override
      public void visitPyFunction(PyFunction node) {
        // Ignore nested functions
      }
    });
    final int n = types.size();
    if (n == 1) {
      elementType = Ref.create(types.iterator().next());
    }
    else if (n > 0) {
      elementType = Ref.create(PyUnionType.union(types));
    }
    if (elementType != null) {
      return Ref.create(PyTypingTypeProvider.wrapInGeneratorType(elementType.get(), getReturnStatementType(context), this));
    }
    if (!types.isEmpty()) {
      return Ref.create(null);
    }
    return null;
  }

  @Override
  @Nullable
  public PyType getReturnStatementType(TypeEvalContext typeEvalContext) {
    final ReturnVisitor visitor = new ReturnVisitor(this, typeEvalContext);
    final PyStatementList statements = getStatementList();
    statements.accept(visitor);
    if (isGeneratedStub() && !visitor.myHasReturns) {
      if (PyNames.INIT.equals(getName())) {
        return PyNoneType.INSTANCE;
      }
      return null;
    }
    return visitor.result();
  }

  @Override
  @Nullable
  public PyFunction asMethod() {
    if (getContainingClass() != null) {
      return this;
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public String getDeprecationMessage() {
    PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getDeprecationMessage();
    }
    return extractDeprecationMessage();
  }

  @Nullable
  public String extractDeprecationMessage() {
    PyStatementList statementList = getStatementList();
    return extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider provider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType type = provider.getCallableType(this, context);
      if (type != null) {
        return type;
      }
    }
    return new PyFunctionTypeImpl(this);
  }

  @Nullable
  public static String extractDeprecationMessage(List<PyStatement> statements) {
    for (PyStatement statement : statements) {
      if (statement instanceof PyExpressionStatement) {
        PyExpressionStatement expressionStatement = (PyExpressionStatement)statement;
        if (expressionStatement.getExpression() instanceof PyCallExpression) {
          PyCallExpression callExpression = (PyCallExpression)expressionStatement.getExpression();
          if (callExpression.isCalleeText(PyNames.WARN)) {
            PyReferenceExpression warningClass = callExpression.getArgument(1, PyReferenceExpression.class);
            if (warningClass != null && (PyNames.DEPRECATION_WARNING.equals(warningClass.getReferencedName()) ||
                                         PyNames.PENDING_DEPRECATION_WARNING.equals(warningClass.getReferencedName()))) {
              return PyPsiUtils.strValue(callExpression.getArguments()[0]);
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public String getDocStringValue() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return DocStringUtil.getDocStringValue(this);
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return CachedValuesManager.getCachedValue(this, myCachedStructuredDocStringProvider);
  }

  private boolean isGeneratedStub() {
    VirtualFile vFile = getContainingFile().getVirtualFile();
    if (vFile != null) {
      vFile = vFile.getParent();
      if (vFile != null) {
        vFile = vFile.getParent();
        if (vFile != null && vFile.getName().equals(PythonSdkType.SKELETON_DIR_NAME)) {
          return true;
        }
      }
    }
    return false;
  }

  private static class ReturnVisitor extends PyRecursiveElementVisitor {
    private final PyFunction myFunction;
    private final TypeEvalContext myContext;
    private PyType myResult = null;
    private boolean myHasReturns = false;
    private boolean myHasRaises = false;

    public ReturnVisitor(PyFunction function, final TypeEvalContext context) {
      myFunction = function;
      myContext = context;
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      if (ScopeUtil.getScopeOwner(node) == myFunction) {
        final PyExpression expr = node.getExpression();
        PyType returnType = expr == null ? PyNoneType.INSTANCE : myContext.getType(expr);
        if (!myHasReturns) {
          myResult = returnType;
          myHasReturns = true;
        }
        else {
          myResult = PyUnionType.union(myResult, returnType);
        }
      }
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      myHasRaises = true;
    }

    @Nullable
    PyType result() {
      return myHasReturns || myHasRaises ? myResult : PyNoneType.INSTANCE;
    }
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFunction(this);
  }

  @Override
  public int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  @Override
  @Nullable
  public PyStringLiteralExpression getDocStringExpression() {
    final PyStatementList stmtList = getStatementList();
    return DocStringUtil.findDocStringExpression(stmtList);
  }

  @Override
  public String toString() {
    return super.toString() + "('" + getName() + "')";
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
    myIsGenerator = null;
  }

  @Override
  @Nullable
  public Property getProperty() {
    final PyClass containingClass = getContainingClass();
    if (containingClass != null) {
      return containingClass.findPropertyByCallable(this);
    }
    return null;
  }

  @Override
  public PyAnnotation getAnnotation() {
    return getStubOrPsiChild(PyElementTypes.ANNOTATION);
  }

  @Nullable
  @Override
  public String getAnnotationValue() {
    return getAnnotationContentFromStubOrPsi(this);
  }

  @Nullable
  @Override
  public PsiComment getTypeComment() {
    final PsiComment inlineComment = PyUtil.getCommentOnHeaderLine(this);
    if (inlineComment != null && PyTypingTypeProvider.getTypeCommentValue(inlineComment.getText()) != null) {
      return inlineComment;
    }

    final PyStatementList statements = getStatementList();
    if (statements.getStatements().length != 0) {
      final PsiComment comment = as(statements.getFirstChild(), PsiComment.class);
      if (comment != null && PyTypingTypeProvider.getTypeCommentValue(comment.getText()) != null) {
        return comment;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getTypeCommentAnnotation() {
    return getTypeCommentAnnotationFromStubOrPsi(this);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
    if (scopeOwner instanceof PyFunction) {
      return new LocalSearchScope(scopeOwner);
    }
    return super.getUseScope();
  }

  /**
   * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
   *
   * @return a flag describing what was detected.
   */
  @Override
  @Nullable
  public Modifier getModifier() {
    final String deconame = getClassOrStaticMethodDecorator();
    if (PyNames.CLASSMETHOD.equals(deconame)) {
      return CLASSMETHOD;
    }
    else if (PyNames.STATICMETHOD.equals(deconame)) {
      return STATICMETHOD;
    }

    final String funcName = getName();

    // implicit staticmethod __new__
    final PyClass cls = getContainingClass();
    if (cls != null && PyNames.NEW.equals(funcName) && cls.isNewStyleClass(null)) {
      return STATICMETHOD;
    }

    // implicit classmethod __init_subclass__
    if (cls != null && PyNames.INIT_SUBCLASS.equals(funcName) && LanguageLevel.forElement(this).isAtLeast(LanguageLevel.PYTHON36)) {
      return CLASSMETHOD;
    }

    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return getModifierFromStub(stub);
    }

    if (funcName != null) {
      PyAssignmentStatement currentAssignment = PsiTreeUtil.getNextSiblingOfType(this, PyAssignmentStatement.class);
      while (currentAssignment != null) {
        final String modifier = currentAssignment
          .getTargetsToValuesMapping()
          .stream()
          .filter(pair -> pair.getFirst() instanceof PyTargetExpression && funcName.equals(pair.getFirst().getName()))
          .filter(pair -> pair.getSecond() instanceof PyCallExpression)
          .map(pair -> interpretAsModifierWrappingCall((PyCallExpression)pair.getSecond()))
          .filter(interpreted -> interpreted != null && interpreted.getSecond() == this)
          .map(interpreted -> interpreted.getFirst())
          .filter(wrapperName -> PyNames.CLASSMETHOD.equals(wrapperName) || PyNames.STATICMETHOD.equals(wrapperName))
          .findAny()
          .orElse(null);

        if (PyNames.CLASSMETHOD.equals(modifier)) {
          return CLASSMETHOD;
        }
        else if (PyNames.STATICMETHOD.equals(modifier)) {
          return STATICMETHOD;
        }

        currentAssignment = PsiTreeUtil.getNextSiblingOfType(currentAssignment, PyAssignmentStatement.class);
      }
    }

    return null;
  }

  @Override
  public boolean isGenerator() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.isGenerator();
    }

    Boolean result = myIsGenerator;
    if (result == null) {
      Ref<Boolean> containsYield = Ref.create(false);
      getStatementList().accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyYieldExpression(PyYieldExpression node) {
          containsYield.set(true);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
          // Ignore nested functions
        }

        @Override
        public void visitElement(PsiElement element) {
          if (!containsYield.get()) {
            super.visitElement(element);
          }
        }
      });
      myIsGenerator = result = containsYield.get();
    }
    return result;
  }

  @Override
  public boolean isAsync() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.isAsync();
    }
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }

  @Override
  public boolean isAsyncAllowed() {
    final LanguageLevel languageLevel = LanguageLevel.forElement(this);
    final String functionName = getName();

    return languageLevel.isAtLeast(LanguageLevel.PYTHON35) && (
      functionName == null ||
      ArrayUtil.contains(functionName, PyNames.AITER, PyNames.ANEXT, PyNames.AENTER, PyNames.AEXIT, PyNames.CALL) ||
      !PyNames.getBuiltinMethods(languageLevel).containsKey(functionName)
    );
  }

  @Override
  public boolean onlyRaisesNotImplementedError() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.onlyRaisesNotImplementedError();
    }

    final PyStatement[] statements = getStatementList().getStatements();
    return statements.length == 1 && isRaiseNotImplementedError(statements[0]) ||
           statements.length == 2 && PyUtil.isStringLiteral(statements[0]) && isRaiseNotImplementedError(statements[1]);
  }

  private static boolean isRaiseNotImplementedError(@NotNull PyStatement statement) {
    final PyExpression raisedExpression = Optional
      .ofNullable(as(statement, PyRaiseStatement.class))
      .map(PyRaiseStatement::getExpressions)
      .filter(expressions -> expressions.length == 1)
      .map(expressions -> expressions[0])
      .orElse(null);

    if (raisedExpression instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)raisedExpression).getCallee();
      if (callee != null && callee.getText().equals(PyNames.NOT_IMPLEMENTED_ERROR)) {
        return true;
      }
    }
    else if (raisedExpression != null && raisedExpression.getText().equals(PyNames.NOT_IMPLEMENTED_ERROR)) {
      return true;
    }

    return false;
  }

  @Nullable
  private static Modifier getModifierFromStub(@NotNull PyFunctionStub stub) {
    return JBIterable
      .of(stub.getParentStub())
      .flatMap((StubElement element) -> (List<StubElement>)element.getChildrenStubs())
      .skipWhile(siblingStub -> !stub.equals(siblingStub))
      .transform(nextSiblingStub -> as(nextSiblingStub, PyTargetExpressionStub.class))
      .filter(Objects::nonNull)
      .filter(nextSiblingStub -> nextSiblingStub.getInitializerType() == PyTargetExpressionStub.InitializerType.CallExpression)
      .transform(PyTargetExpressionStub::getInitializer)
      .transform(
        initializerName -> {
          if (initializerName == null) {
            return null;
          }
          else if (initializerName.matches(PyNames.CLASSMETHOD)) {
            return CLASSMETHOD;
          }
          else if (initializerName.matches(PyNames.STATICMETHOD)) {
            return STATICMETHOD;
          }
          else {
            return null;
          }
        }
      )
      .find(Objects::nonNull);
  }

  /**
   * When a function is decorated many decorators, finds the deepest builtin decorator:
   * <pre>
   * &#x40;foo
   * &#x40;classmethod <b># &lt;-- that's it</b>
   * &#x40;bar
   * def moo(cls):
   * &nbsp;&nbsp;pass
   * </pre>
   *
   * @return name of the built-in decorator, or null (even if there are non-built-in decorators).
   */
  @Nullable
  private String getClassOrStaticMethodDecorator() {
    PyDecoratorList decolist = getDecoratorList();
    if (decolist != null) {
      PyDecorator[] decos = decolist.getDecorators();
      if (decos.length > 0) {
        for (int i = decos.length - 1; i >= 0; i -= 1) {
          PyDecorator deco = decos[i];
          String deconame = deco.getName();
          if (PyNames.CLASSMETHOD.equals(deconame) || PyNames.STATICMETHOD.equals(deconame)) {
            return deconame;
          }
          for (PyKnownDecoratorProvider provider : PyUtil.KnownDecoratorProviderHolder.KNOWN_DECORATOR_PROVIDERS) {
            String name = provider.toKnownDecorator(deconame);
            if (name != null) {
              return name;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }

  @NotNull
  @Override
  public List<PyAssignmentStatement> findAttributes() {
    /**
     * TODO: This method if insanely heavy since it unstubs foreign files.
     * Need to save stubs and use them somehow.
     *
     */
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, ATTRIBUTES_KEY, () -> {
      final List<PyAssignmentStatement> result = findAttributesStatic(this);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }, false);
  }

  /**
   * @param self should be this
   */
  @NotNull
  private static List<PyAssignmentStatement> findAttributesStatic(@NotNull final PsiElement self) {
    final List<PyAssignmentStatement> result = new ArrayList<>();
    for (final PyAssignmentStatement statement : new PsiQuery<>(self).siblings(PyAssignmentStatement.class)
      .getElements()) {
      for (final PyQualifiedExpression targetExpression : new PsiQuery<PsiElement>(statement.getTargets())
        .filter(new PsiQuery.PsiFilter<>(PyQualifiedExpression.class))
        .getElements()) {
        final PyExpression qualifier = targetExpression.getQualifier();
        if (qualifier == null) {
          continue;
        }
        final PsiReference qualifierReference = qualifier.getReference();
        if (qualifierReference == null) {
          continue;
        }
        if (qualifierReference.isReferenceTo(self)) {
          result.add(statement);
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public ProtectionLevel getProtectionLevel() {
    final int underscoreLevels = PyUtil.getInitialUnderscores(getName());
    for (final ProtectionLevel level : ProtectionLevel.values()) {
      if (level.getUnderscoreLevel() == underscoreLevels) {
        return level;
      }
    }
    return ProtectionLevel.PRIVATE;
  }
}
