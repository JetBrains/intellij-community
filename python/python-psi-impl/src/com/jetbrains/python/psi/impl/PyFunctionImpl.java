// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.JBIterable;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.codeInsight.controlflow.*;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyAnnotationOwnerStub;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.STATICMETHOD;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.interpretAsModifierWrappingCall;
import static com.jetbrains.python.psi.impl.PyDeprecationUtilKt.extractDeprecationMessageFromDecorator;

public class PyFunctionImpl extends PyBaseElementImpl<PyFunctionStub> implements PyFunction {

  private static final Key<CachedValue<List<PyAssignmentStatement>>>
    ATTRIBUTES_KEY = Key.create("attributes");

  public PyFunctionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFunctionImpl(final PyFunctionStub stub) {
    this(stub, PyStubElementTypes.FUNCTION_DECLARATION);
  }

  public PyFunctionImpl(PyFunctionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  private class CachedStructuredDocStringProvider implements CachedValueProvider<StructuredDocString> {
    @Override
    public @Nullable Result<StructuredDocString> compute() {
      final PyFunctionImpl f = PyFunctionImpl.this;
      return Result.create(DocStringUtil.getStructuredDocString(f), f);
    }
  }

  private final @NotNull CachedStructuredDocStringProvider myCachedStructuredDocStringProvider = new CachedStructuredDocStringProvider();
  private volatile @Nullable Boolean myIsGenerator;

  @Override
  public @Nullable String getName() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }

    return PyFunction.super.getName();
  }

  @Override
  public @NotNull PsiElement setName(@NotNull String name) throws IncorrectOperationException {
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
        return PythonPsiApiIcons.PropertyGetter;
      }
      if (property.getSetter().valueOrNull() == this) {
        return PythonPsiApiIcons.PropertySetter;
      }
      if (property.getDeleter().valueOrNull() == this) {
        return PythonPsiApiIcons.PropertyDeleter;
      }
      return PlatformIcons.PROPERTY_ICON;
    }
    IconManager iconManager = IconManager.getInstance();
    if (getContainingClass() != null) {
      return iconManager.getPlatformIcon(com.intellij.ui.PlatformIcons.Method);
    }
    return iconManager.getPlatformIcon(com.intellij.ui.PlatformIcons.Function);
  }

  @Override
  public @NotNull List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return Optional
      .ofNullable(context.getType(this))
      .filter(PyCallableType.class::isInstance)
      .map(PyCallableType.class::cast)
      .map(callableType -> callableType.getParameters(context))
      .orElseGet(() -> map(getParameterList().getParameters(), PyCallableParameterImpl::psi));
  }

  @Override
  public @Nullable PyClass getContainingClass() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof PyClassStub) {
        return ((PyClassStub)parentStub).getPsi();
      }

      return null;
    }

    return PyFunction.super.getContainingClass();
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final Ref<PyType> returnTypeRef = typeProvider.getReturnType(this, context);
      if (returnTypeRef != null) {
        return PyTypingTypeProvider.removeNarrowedTypeIfNeeded(derefType(returnTypeRef, typeProvider));
      }
    }

    return getInferredReturnType(context);
  }

  @Override
  public @Nullable PyType getInferredReturnType(@NotNull TypeEvalContext context) {
    PyType inferredType = null;
    if (context.allowReturnTypes(this)) {
      final PyType returnType = getReturnStatementType(context);
      final Pair<PyType, PyType> yieldSendTypePair = getYieldExpressionType(context);
      if (yieldSendTypePair != null) {
        inferredType = PyTypingTypeProvider.wrapInGeneratorType(yieldSendTypePair.first, yieldSendTypePair.second, returnType, this);
      }
      else {
        inferredType = returnType;
      }
    }
    inferredType = PyNeverType.toNoReturnIfNeeded(inferredType);
    return PyTypingTypeProvider.removeNarrowedTypeIfNeeded(PyTypingTypeProvider.toAsyncIfNeeded(this, inferredType));
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final Ref<PyType> typeRef = typeProvider.getCallType(this, callSite, context);
      if (typeRef != null) {
        return derefType(typeRef, typeProvider);
      }
    }

    final PyExpression receiver = callSite.getReceiver(this);
    final PyCallExpression.PyArgumentsMapping fullMapping = PyCallExpressionHelper.mapArguments(callSite, this, context);
    final Map<PyExpression, PyCallableParameter> mappedExplicitParameters = fullMapping.getMappedParameters();

    final Map<PyExpression, PyCallableParameter> allMappedParameters = new LinkedHashMap<>();
    final PyCallableParameter firstImplicit = getFirstItem(fullMapping.getImplicitParameters());
    if (receiver != null && firstImplicit != null) {
      allMappedParameters.put(receiver, firstImplicit);
    }
    allMappedParameters.putAll(mappedExplicitParameters);

    return getCallType(receiver, callSite, allMappedParameters, context);
  }

  private static @Nullable PyType derefType(@NotNull Ref<PyType> typeRef, @NotNull PyTypeProvider typeProvider) {
    final PyType type = typeRef.get();
    if (type != null) {
      type.assertValid(typeProvider.toString());
    }
    return type;
  }

  @Override
  public @Nullable PyType getCallType(@Nullable PyExpression receiver,
                                      @Nullable PyCallSiteExpression callSiteExpression,
                                      @NotNull Map<PyExpression, PyCallableParameter> parameters,
                                      @NotNull TypeEvalContext context) {
    return analyzeCallType(PyUtil.getReturnTypeToAnalyzeAsCallType(this, context), receiver, callSiteExpression, parameters, context);
  }

  private @Nullable PyType analyzeCallType(@Nullable PyType type,
                                           @Nullable PyExpression receiver,
                                           @Nullable PyCallSiteExpression callSiteExpression,
                                           @NotNull Map<PyExpression, PyCallableParameter> parameters,
                                           @NotNull TypeEvalContext context) {
    if (PyTypeChecker.hasGenerics(type, context)) {
      final var substitutions = PyTypeChecker.unifyGenericCall(receiver, parameters, context);
      if (substitutions != null) {
        final var substitutionsWithUnresolvedReturnGenerics =
          PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(getParameters(context), type, substitutions, context);
        type = PyTypeChecker.substitute(type, substitutionsWithUnresolvedReturnGenerics, context);
      }
      else {
        type = null;
      }
    }
    else if (receiver != null) {
      type = replaceSelf(type, receiver, context);
    }
    if (type != null && isDynamicallyEvaluated(parameters.values(), context)) {
      type = PyUnionType.createWeakType(type);
    }
    return PyNarrowedType.Companion.bindIfNeeded(type, callSiteExpression);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this) {
      @Override
      public @NotNull String getPresentableText() {
        return notNullize(getName(), PyNames.UNNAMED_ELEMENT) + getParameterList().getPresentableText(true);
      }
    };
  }

  private @Nullable PyType replaceSelf(@Nullable PyType returnType, @Nullable PyExpression receiver, @NotNull TypeEvalContext context) {
    return replaceSelf(returnType, receiver, context, true);
  }

  private @Nullable PyType replaceSelf(@Nullable PyType returnType,
                                       @Nullable PyExpression receiver,
                                       @NotNull TypeEvalContext context,
                                       boolean allowCoroutineOrGenerator) {
    if (receiver != null) {
      // TODO: Currently we substitute only simple subclass types and unions, but we could handle collection types as well
      if (returnType instanceof PyClassType returnClassType) {

        if (returnClassType.getPyClass() == getContainingClass()) {
          final PyType receiverType = context.getType(receiver);

          if (receiverType instanceof PyClassType receiverClassType) {

            if (receiverClassType.getPyClass() != returnClassType.getPyClass() &&
                PyTypeChecker.match(returnClassType.toClass(), receiverClassType.toClass(), context)) {
              return returnClassType.isDefinition() ? receiverClassType.toClass() : receiverClassType.toInstance();
            }
          }
        }
        else if (allowCoroutineOrGenerator &&
                 returnType instanceof PyCollectionType &&
                 PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType) != null) {
          final List<PyType> replacedElementTypes = map(
            ((PyCollectionType)returnType).getElementTypes(),
            type -> replaceSelf(type, receiver, context, false)
          );

          return new PyCollectionTypeImpl(returnClassType.getPyClass(),
                                          returnClassType.isDefinition(),
                                          replacedElementTypes);
        }
      }
      else if (returnType instanceof PyUnionType) {
        return ((PyUnionType)returnType).map(type -> replaceSelf(type, receiver, context, true));
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

  public static class YieldCollector extends PyRecursiveElementVisitor {
    public List<PyYieldExpression> getYieldExpressions() {
      return myYieldExpressions;
    }

    private final List<PyYieldExpression> myYieldExpressions = new ArrayList<>();

    @Override
    public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
      myYieldExpressions.add(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      // Ignore nested functions
    }

    @Override
    public void visitPyLambdaExpression(@NotNull PyLambdaExpression node) {
      // Ignore nested lambdas
    }
  }

  /**
   * @return pair of YieldType and SendType. Null when there are no yields
   */
  private @Nullable Pair<PyType, PyType> getYieldExpressionType(final @NotNull TypeEvalContext context) {
    final PyStatementList statements = getStatementList();
    final YieldCollector visitor = new YieldCollector();
    statements.accept(visitor);
    final List<PyType> yieldTypes = map(visitor.getYieldExpressions(), it -> it.getYieldType(context));
    final List<PyType> sendTypes = map(visitor.getYieldExpressions(), it -> it.getSendType(context));
    if (!yieldTypes.isEmpty()) {
      return Pair.create(PyUnionType.union(yieldTypes), PyUnionType.union(sendTypes));
    }
    return null;
  }

  @Override
  public @Nullable PyType getReturnStatementType(@NotNull TypeEvalContext context) {
    return PyUtil.getNullableParameterizedCachedValue(this, context, (it) -> getReturnStatementTypeNoCache(it));
  }

  private @Nullable PyType getReturnStatementTypeNoCache(@NotNull TypeEvalContext context) {
    final List<PyStatement> returnPoints = getReturnPoints(context);
    final List<PyType> types = new ArrayList<>();
    boolean hasReturn = false;

    for (var point : returnPoints) {
      if (point instanceof PyReturnStatement returnStatement) {
        hasReturn = true;
        final PyExpression expr = returnStatement.getExpression();
        types.add(expr != null ? context.getType(expr) : PyBuiltinCache.getInstance(this).getNoneType());
      }
      else {
        types.add(PyBuiltinCache.getInstance(this).getNoneType());
      }
    }

    if ((isGeneratedStub() || PyKnownDecoratorUtil.hasAbstractDecorator(this, context)) && !hasReturn) {
      if (PyUtil.isInitMethod(this)) {
        return PyBuiltinCache.getInstance(this).getNoneType();
      }
      return null;
    }
    return PyUnionType.union(types);
  }

  @Override
  public @NotNull List<PyStatement> getReturnPoints(@NotNull TypeEvalContext context) {
    final Instruction[] flow = ControlFlowCache.getControlFlow(this).getInstructions();
    final PyDataFlow dataFlow = ControlFlowCache.getDataFlow(this, context);

    class ReturnPointCollector {
      final List<PyStatement> returnPoints = new ArrayList<>();
      boolean collectImplicitReturn = true;

      ControlFlowUtil.Operation checkInstruction(@NotNull Instruction instruction) {
        if (dataFlow.isUnreachable(instruction)) {
          return ControlFlowUtil.Operation.CONTINUE;
        }
        if (instruction instanceof PyFinallyFailExitInstruction exitInstruction) {
          // Most nodes in try-part are connected to finally-fail-part,
          // but we will only be interested in explicit return statements.
          boolean oldCollectImplicitReturn = collectImplicitReturn;
          collectImplicitReturn = false;
          walkCFG(ArrayUtil.indexOf(flow, exitInstruction.getBegin()));
          collectImplicitReturn = oldCollectImplicitReturn;
          return ControlFlowUtil.Operation.CONTINUE;
        }
        if (instruction instanceof CallInstruction ci && ci.isNoReturnCall(context)) {
          return ControlFlowUtil.Operation.CONTINUE;
        }
        if (instruction instanceof PyRaiseInstruction) {
          return ControlFlowUtil.Operation.CONTINUE;
        }
        if (instruction instanceof PyWithContextExitInstruction withExit) {
          if (collectImplicitReturn && withExit.isSuppressingExceptions(context)) {
            returnPoints.add(PsiTreeUtil.getParentOfType(withExit.getElement(), PyWithStatement.class));
          }
          return ControlFlowUtil.Operation.CONTINUE;
        }
        final PsiElement element = instruction.getElement();
        if (!(element instanceof PyStatement statement)) {
          return ControlFlowUtil.Operation.NEXT;
        }
        if (collectImplicitReturn || statement instanceof PyReturnStatement) {
          returnPoints.add(statement);
        }
        return ControlFlowUtil.Operation.CONTINUE;
      }

      void walkCFG(int startInstruction) {
        ControlFlowUtil.iteratePrev(startInstruction, flow, this::checkInstruction);
      }
    }

    ReturnPointCollector collector = new ReturnPointCollector();
    collector.walkCFG(flow.length - 1);
    return collector.returnPoints;
  }

  @Override
  public @Nullable String getDeprecationMessage() {
    PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getDeprecationMessage();
    }
    return extractDeprecationMessage();
  }

  public @Nullable String extractDeprecationMessage() {
    String deprecationMessageFromDecorator = extractDeprecationMessageFromDecorator(this);
    if (deprecationMessageFromDecorator != null) {
      return deprecationMessageFromDecorator;
    }
    PyStatementList statementList = getStatementList();
    return PyFunction.extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
      final PyType type = provider.getCallableType(this, context);
      if (type != null) {
        return type;
      }
    }
    return PyFunctionTypeImpl.create(this, context);
  }

  @Override
  public String getDocStringValue() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return PyFunction.super.getDocStringValue();
  }

  @Override
  public @Nullable StructuredDocString getStructuredDocString() {
    return CachedValuesManager.getCachedValue(this, myCachedStructuredDocStringProvider);
  }

  private boolean isGeneratedStub() {
    VirtualFile vFile = getContainingFile().getVirtualFile();
    if (vFile != null) {
      vFile = vFile.getParent();
      if (vFile != null) {
        vFile = vFile.getParent();
        if (vFile != null && vFile.getName().equals(PythonSdkUtil.SKELETON_DIR_NAME)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFunction(this);
  }

  @Override
  public int getTextOffset() {
    return PyFunction.super.getTextOffset();
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
  public @Nullable Property getProperty() {
    final PyClass containingClass = getContainingClass();
    if (containingClass != null) {
      return containingClass.findPropertyByCallable(this);
    }
    return null;
  }

  @Override
  public @Nullable String getAnnotationValue() {
    final PyAnnotationOwnerStub stub = getStub();
    if (stub != null) {
      return stub.getAnnotation();
    }
    return PyFunction.super.getAnnotationValue();
  }

  @Override
  public @Nullable String getTypeCommentAnnotation() {
    return getTypeCommentAnnotationFromStubOrPsi(this);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
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
  public @Nullable Modifier getModifier() {
    final PyKnownDecorator decorator = getClassOrStaticMethodDecorator();
    if (decorator != null) {
      if (decorator.isClassMethod()) {
        return CLASSMETHOD;
      }
      else if (decorator.isStaticMethod()) {
        return STATICMETHOD;
      }
    }

    final String funcName = getName();

    final PyClass cls = getContainingClass();
    if (cls != null) {
      // implicit staticmethod __new__
      if (PyNames.NEW.equals(funcName) && cls.isNewStyleClass(null)) {
        return STATICMETHOD;
      }

      final LanguageLevel level = LanguageLevel.forElement(this);

      // implicit classmethod __init_subclass__
      if (PyNames.INIT_SUBCLASS.equals(funcName) && level.isAtLeast(LanguageLevel.PYTHON36)) {
        return CLASSMETHOD;
      }

      // implicit classmethod __class_getitem__
      if (PyNames.CLASS_GETITEM.equals(funcName) && level.isAtLeast(LanguageLevel.PYTHON37)) {
        return CLASSMETHOD;
      }
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
        public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
          containsYield.set(true);
        }

        @Override
        public void visitPyFunction(@NotNull PyFunction node) {
          // Ignore nested functions
        }

        @Override
        public void visitElement(@NotNull PsiElement element) {
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
    return PyFunction.super.isAsync();
  }

  @Override
  public boolean onlyRaisesNotImplementedError() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.onlyRaisesNotImplementedError();
    }

    return PyFunction.super.onlyRaisesNotImplementedError();
  }

  private static @Nullable Modifier getModifierFromStub(@NotNull PyFunctionStub stub) {
    return JBIterable
      .of(stub.getParentStub())
      .flatMap((StubElement element) -> (List<StubElement>)element.getChildrenStubs())
      .skipWhile(siblingStub -> !stub.equals(siblingStub))
      .transform(nextSiblingStub -> as(nextSiblingStub, PyTargetExpressionStub.class))
      .filter(Objects::nonNull)
      .filter(nextSiblingStub -> nextSiblingStub.getInitializerType() == PyTargetExpressionStub.InitializerType.CallExpression &&
                                 Objects.equals(stub.getName(), nextSiblingStub.getName()))
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
  private @Nullable PyKnownDecorator getClassOrStaticMethodDecorator() {
    PyDecoratorList decolist = getDecoratorList();
    if (decolist != null) {
      PyDecorator[] decos = decolist.getDecorators();
      if (decos.length > 0) {
        for (int i = decos.length - 1; i >= 0; i -= 1) {
          PyDecorator deco = decos[i];
          List<PyKnownDecorator> knownDecorators = PyKnownDecoratorUtil.asKnownDecorators(deco, TypeEvalContext.codeInsightFallback(getProject()));
          for (PyKnownDecorator decorator : knownDecorators) {
            if (decorator.isClassMethod() || decorator.isStaticMethod()) {
              return decorator;
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }

  @Override
  public @NotNull List<PyAssignmentStatement> findAttributes() {
    /*
     * TODO: This method if insanely heavy since it unstubs foreign files.
     * Need to save stubs and use them somehow.
     *
     */
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, ATTRIBUTES_KEY, () -> {
      final List<PyAssignmentStatement> result = findAttributesStatic(this);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }, false);
  }

  @Override
  public @NotNull PyParameterList getParameterList() {
    return getRequiredStubOrPsiChild(PyStubElementTypes.PARAMETER_LIST);
  }

  @Override
  public @Nullable PyTypeParameterList getTypeParameterList() {
    return getStubOrPsiChild(PyStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public @Nullable PyDecoratorList getDecoratorList() {
    return getStubOrPsiChild(PyStubElementTypes.DECORATOR_LIST);
  }

  @Override
  public @Nullable PyAnnotation getAnnotation() {
    return getStubOrPsiChild(PyStubElementTypes.ANNOTATION);
  }

  /**
   * is `function` a method or a classmethod
   */
  public static boolean isMethod(PyFunction function) {
    final var isMethod = ScopeUtil.getScopeOwner(function) instanceof PyClass;
    final var modifier = function.getModifier();
    return (isMethod && modifier == null) || modifier == CLASSMETHOD;
  }

  /**
   * @param self should be this
   */
  private static @NotNull List<PyAssignmentStatement> findAttributesStatic(final @NotNull PsiElement self) {
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
}
