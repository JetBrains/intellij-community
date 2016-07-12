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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
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

  private CachedStructuredDocStringProvider myCachedStructuredDocStringProvider = new CachedStructuredDocStringProvider();

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

  public PsiElement getNameIdentifier() {
    final ASTNode nameNode = getNameNode();
    return nameNode != null ? nameNode.getPsi() : null;
  }

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

  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @NotNull
  public PyParameterList getParameterList() {
    return getRequiredStubOrPsiChild(PyElementTypes.PARAMETER_LIST);
  }

  @Override
  @NotNull
  public PyStatementList getStatementList() {
    final PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
    assert statementList != null : "Statement list missing for function " + getText();
    return statementList;
  }

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

  @Nullable
  public PyDecoratorList getDecoratorList() {
    return getStubOrPsiChild(PyElementTypes.DECORATOR_LIST); // PsiTreeUtil.getChildOfType(this, PyDecoratorList.class);
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType type = getReturnType(context);
    return isAsync() ? createCoroutineType(type) : type;
  }

  @Nullable
  private PyType getReturnType(@NotNull TypeEvalContext context) {
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final Ref<PyType> returnTypeRef = typeProvider.getReturnType(this, context);
      if (returnTypeRef != null) {
        final PyType returnType = returnTypeRef.get();
        if (returnType != null) {
          returnType.assertValid(typeProvider.toString());
        }
        return returnType;
      }
    }
    if (context.allowReturnTypes(this)) {
      final Ref<? extends PyType> yieldTypeRef = getYieldStatementType(context);
      if (yieldTypeRef != null) {
        return yieldTypeRef.get();
      }
      return getReturnStatementType(context);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType type = typeProvider.getCallType(this, callSite, context);
      if (type != null) {
        type.assertValid(typeProvider.toString());
        return type;
      }
    }
    final PyExpression receiver = PyTypeChecker.getReceiver(callSite, this);
    final List<PyExpression> arguments = PyTypeChecker.getArguments(callSite, this);
    final List<PyParameter> parameters = PyUtil.getParameters(this, context);
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final List<PyParameter> explicitParameters = PyTypeChecker.filterExplicitParameters(parameters, this, callSite, resolveContext);
    final Map<PyExpression, PyNamedParameter> mapping = PyCallExpressionHelper.mapArguments(arguments, explicitParameters);
    return getCallType(receiver, mapping, context);
  }

  @Nullable
  @Override
  public PyType getCallType(@Nullable PyExpression receiver,
                            @NotNull Map<PyExpression, PyNamedParameter> parameters,
                            @NotNull TypeEvalContext context) {
    return analyzeCallType(context.getReturnType(this), receiver, parameters, context);
  }

  @Nullable
  private PyType analyzeCallType(@Nullable PyType type,
                                 @Nullable PyExpression receiver,
                                 @NotNull Map<PyExpression, PyNamedParameter> parameters,
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
      @Nullable
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
    if (receiver != null) {
      // TODO: Currently we substitute only simple subclass types, but we could handle union and collection types as well
      if (returnType instanceof PyClassType) {
        final PyClassType returnClassType = (PyClassType)returnType;
        if (returnClassType.getPyClass() == getContainingClass()) {
          final PyType receiverType = context.getType(receiver);
          if (receiverType instanceof PyClassType && PyTypeChecker.match(returnType, receiverType, context)) {
            return returnClassType.isDefinition() ? receiverType : ((PyClassType)receiverType).toInstance();
          }
        }
      }
    }
    return returnType;
  }

  private static boolean isDynamicallyEvaluated(@NotNull Collection<PyNamedParameter> parameters, @NotNull TypeEvalContext context) {
    for (PyNamedParameter parameter : parameters) {
      final PyType type = context.getType(parameter);
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
    final Set<PyType> types = new LinkedHashSet<PyType>();
    statements.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyYieldExpression(PyYieldExpression node) {
        final PyType type = context.getType(node);
        if (node.isDelegating() && type instanceof PyCollectionType) {
          final PyCollectionType collectionType = (PyCollectionType)type;
          // TODO: Select the parameter types that matches T in Iterable[T]
          final List<PyType> elementTypes = collectionType.getElementTypes(context);
          types.add(elementTypes.isEmpty() ? null : elementTypes.get(0));
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
      final PyClass generator = cache.getClass(PyNames.FAKE_GENERATOR);
      if (generator != null) {
        final List<PyType> parameters = Arrays.asList(elementType.get(), null, getReturnStatementType(context));
        return Ref.create(new PyCollectionTypeImpl(generator, false, parameters));
      }
    }
    if (!types.isEmpty()) {
      return Ref.create(null);
    }
    return null;
  }

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

  @Nullable
  private PyType createCoroutineType(@Nullable PyType returnType) {
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
    if (returnType instanceof PyClassLikeType && PyNames.FAKE_COROUTINE.equals(((PyClassLikeType)returnType).getClassQName())) {
      return returnType;
    }
    final PyClass generator = cache.getClass(PyNames.FAKE_COROUTINE);
    return generator != null ? new PyCollectionTypeImpl(generator, false, Collections.singletonList(returnType)) : null;
  }

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
  public PyType getReturnTypeFromDocString() {
    final String typeName = extractReturnType();
    return typeName != null ? PyTypeParser.getTypeByName(this, typeName) : null;
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
    final PyFunctionTypeImpl type = new PyFunctionTypeImpl(this);
    if (PyKnownDecoratorUtil.hasUnknownDecorator(this, context) && getProperty() == null) {
      return PyUnionType.createWeakType(type);
    }
    return type;
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

  @Nullable
  private String extractReturnType() {
    final String ARROW = "->";
    final StructuredDocString structuredDocString = getStructuredDocString();
    if (structuredDocString != null) {
      return structuredDocString.getReturnType();
    }
    final String docString = getDocStringValue();
    if (docString != null && docString.contains(ARROW)) {
      final List<String> lines = StringUtil.split(docString, "\n");
      while (lines.size() > 0 && lines.get(0).trim().length() == 0) {
        lines.remove(0);
      }
      if (lines.size() > 1 && lines.get(1).trim().length() == 0) {
        String firstLine = lines.get(0);
        int pos = firstLine.lastIndexOf(ARROW);
        if (pos >= 0) {
          return firstLine.substring(pos + 2).trim();
        }
      }
    }
    return null;
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
      if (PsiTreeUtil.getParentOfType(node, ScopeOwner.class, true) == myFunction) {
        final PyExpression expr = node.getExpression();
        PyType returnType;
        returnType = expr == null ? PyNoneType.INSTANCE : myContext.getType(expr);
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

  public int getTextOffset() {
    final ASTNode name = getNameNode();
    return name != null ? name.getStartOffset() : super.getTextOffset();
  }

  public PyStringLiteralExpression getDocStringExpression() {
    final PyStatementList stmtList = getStatementList();
    return DocStringUtil.findDocStringExpression(stmtList);
  }

  @Override
  public String toString() {
    return super.toString() + "('" + getName() + "')";
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
  }

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
  public PsiComment getTypeComment() {
    final PyStatementList statements = getStatementList();
    final PsiComment inlineComment = as(PyPsiUtils.getPrevNonWhitespaceSibling(statements), PsiComment.class);
    if (inlineComment != null && PyTypingTypeProvider.getTypeCommentValue(inlineComment.getText()) != null) {
      return inlineComment;
    }

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
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getTypeComment(); 
    }
    final PsiComment comment = getTypeComment();
    if (comment != null) {
      return PyTypingTypeProvider.getTypeCommentValue(comment.getText());
    }
    return null;
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
  @Nullable
  public Modifier getModifier() {
    final String deconame = getClassOrStaticMethodDecorator();
    if (PyNames.CLASSMETHOD.equals(deconame)) {
      return CLASSMETHOD;
    }
    else if (PyNames.STATICMETHOD.equals(deconame)) {
      return STATICMETHOD;
    }
    // implicit staticmethod __new__
    final PyClass cls = getContainingClass();
    if (cls != null && PyNames.NEW.equals(getName()) && cls.isNewStyleClass(null)) {
      return STATICMETHOD;
    }
    //
    if (getStub() != null) {
      return getWrappersFromStub();
    }
    final String funcName = getName();
    if (funcName != null) {
      PyAssignmentStatement currentAssignment = PsiTreeUtil.getNextSiblingOfType(this, PyAssignmentStatement.class);
      while (currentAssignment != null) {
        final String modifier = currentAssignment
          .getTargetsToValuesMapping()
          .stream()
          .filter(pair -> pair.getFirst() instanceof PyTargetExpression && funcName.equals(pair.getFirst().getName()))
          .filter(pair -> pair.getSecond() instanceof PyCallExpression)
          .map(pair -> interpretAsModifierWrappingCall((PyCallExpression)pair.getSecond(), this))
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
  public boolean isAsync() {
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.isAsync();
    }
    return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
  }

  @Nullable
  private Modifier getWrappersFromStub() {
    final StubElement parentStub = getStub().getParentStub();
    final List childrenStubs = parentStub.getChildrenStubs();
    int index = childrenStubs.indexOf(getStub());
    if (index >= 0 && index < childrenStubs.size() - 1) {
      StubElement nextStub = (StubElement)childrenStubs.get(index + 1);
      if (nextStub instanceof PyTargetExpressionStub) {
        final PyTargetExpressionStub targetExpressionStub = (PyTargetExpressionStub)nextStub;
        if (targetExpressionStub.getInitializerType() == PyTargetExpressionStub.InitializerType.CallExpression) {
          final QualifiedName qualifiedName = targetExpressionStub.getInitializer();
          if (QualifiedName.fromComponents(PyNames.CLASSMETHOD).equals(qualifiedName)) {
            return CLASSMETHOD;
          }
          if (QualifiedName.fromComponents(PyNames.STATICMETHOD).equals(qualifiedName)) {
            return STATICMETHOD;
          }
        }
      }
    }
    return null;
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
      final List<PyAssignmentStatement> result = findAttributesStatic(PyFunctionImpl.this);
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    }, false);
  }

  /**
   * @param self should be this
   */
  @NotNull
  private static List<PyAssignmentStatement> findAttributesStatic(@NotNull final PsiElement self) {
    final List<PyAssignmentStatement> result = new ArrayList<PyAssignmentStatement>();
    for (final PyAssignmentStatement statement : new PsiQuery(self).siblings(PyAssignmentStatement.class).getElements()) {
      for (final PyQualifiedExpression targetExpression : new PsiQuery(statement.getTargets()).filter(PyQualifiedExpression.class)
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
