package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.documentation.StructuredDocString;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.interpretAsModifierWrappingCall;

/**
 * Implements PyFunction.
 */
public class PyFunctionImpl extends PyPresentableElementImpl<PyFunctionStub> implements PyFunction {
  private static final Icon PROPERTY_GETTER = IconLoader.findIcon("/com/jetbrains/python/icons/propertyGetter.png");
  private static final Icon PROPERTY_SETTER = IconLoader.findIcon("/com/jetbrains/python/icons/propertySetter.png");
  private static final Icon PROPERTY_DELETER = IconLoader.findIcon("/com/jetbrains/python/icons/propertyDeleter.png");

  public PyFunctionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFunctionImpl(final PyFunctionStub stub) {
    this(stub, PyElementTypes.FUNCTION_DECLARATION);
  }

  public PyFunctionImpl(PyFunctionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

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
    final ASTNode nameElement = PyElementGenerator.getInstance(getProject()).createNameIdentifier(name);
    final ASTNode nameNode = getNameNode();
    if (nameNode != null) {
      getNode().replaceChild(nameNode, nameElement);
    }
    return this;
  }

  @Override
  public Icon getIcon(int flags) {
    if (isValid()) {
      final Property property = getProperty();
      if (property != null) {
        if (property.getGetter().valueOrNull() == this) {
          return PROPERTY_GETTER;
        }
        if (property.getSetter().valueOrNull() == this) {
          return PROPERTY_SETTER;
        }
        if (property.getDeleter().valueOrNull() == this) {
          return PROPERTY_DELETER;
        }
        return PlatformIcons.PROPERTY_ICON;
      }
    }
    return PlatformIcons.METHOD_ICON;
  }

  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @NotNull
  public PyParameterList getParameterList() {
    return getRequiredStubOrPsiChild(PyElementTypes.PARAMETER_LIST);
  }

  @Nullable
  public PyStatementList getStatementList() {
    return childToPsi(PyElementTypes.STATEMENT_LIST);
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

    final PsiElement parent = getParent();
    if (parent instanceof PyStatementList) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof PyClass) {
        return (PyClass)pparent;
      }
    }
    return null;
  }

  @Nullable
  public PyDecoratorList getDecoratorList() {
    return getStubOrPsiChild(PyElementTypes.DECORATOR_LIST); // PsiTreeUtil.getChildOfType(this, PyDecoratorList.class);
  }

  public boolean isTopLevel() {
    return getParentByStub() instanceof PsiFile;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    final PyType type = getGenericReturnType(context, callSite);
    if (callSite == null) {
      return type;
    }
    if (PyTypeChecker.hasGenerics(type, context)) {
      final PyTypeChecker.AnalyzeCallResults results = PyTypeChecker.analyzeCallSite(callSite, context);
      if (results != null) {
        final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(this, results.getReceiver(), results.getArguments(),
                                                                                        context);
        if (substitutions != null) {
          final Ref<PyType> result = PyTypeChecker.substitute(type, substitutions, context);
          if (result != null) {
            return result.get();
          }
        }
      }
      return null;
    }
    return type;
  }

  @Nullable
  public PyType getReturnType(@NotNull TypeEvalContext context,
                              @Nullable PyExpression receiver,
                              @NotNull Map<PyExpression, PyNamedParameter> arguments) {
    final PyType type = getGenericReturnType(context, null);
    if (PyTypeChecker.hasGenerics(type, context)) {
      final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(this, receiver, arguments, context);
      if (substitutions != null) {
        final Ref<PyType> result = PyTypeChecker.substitute(type, substitutions, context);
        if (result != null) {
          return result.get();
        }
      }
      return null;
    }
    return type;
  }

  @Nullable
  private PyType getGenericReturnType(TypeEvalContext typeEvalContext, @Nullable PyQualifiedExpression callSite) {
    if (typeEvalContext.maySwitchToAST(this)) {
      PyAnnotation anno = getAnnotation();
      if (anno != null) {
        PyClass pyClass = anno.resolveToClass();
        if (pyClass != null) {
          return new PyClassType(pyClass, false);
        }
      }
    }
    for (PyTypeProvider typeProvider : Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType returnType = typeProvider.getReturnType(this, callSite, typeEvalContext);
      if (returnType != null) {
        return returnType;
      }
    }
    final PyType docStringType = getReturnTypeFromDocString();
    if (docStringType != null) {
      return docStringType;
    }
    if (typeEvalContext.allowReturnTypes(this)) {
      final PyType yieldType = getYieldStatementType(typeEvalContext);
      if (yieldType != null) {
        return yieldType;
      }
      return getReturnStatementType(typeEvalContext);
    }
    return null;
  }

  @Nullable
  private PyType getYieldStatementType(@NotNull final TypeEvalContext context) {
    Ref<PyType> elementType = null;
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
    final PyClass listClass = cache.getClass("list");
    final PyStatementList statements = getStatementList();
    final Set<PyType> types = new HashSet<PyType>();
    if (statements != null && listClass != null) {
      statements.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyYieldExpression(PyYieldExpression node) {
          types.add(node.getType(context));
        }
      });
      final int n = types.size();
      if (n == 1) {
        elementType = Ref.create(types.iterator().next());
      }
      else if (n > 0) {
        elementType = Ref.create(PyUnionType.union(types));
      }
    }
    if (elementType != null) {
      final PyType it = PyTypeParser.getTypeByName(this, "Iterator");
      if (it instanceof PyClassType) {
        return new PyCollectionTypeImpl(((PyClassType)it).getPyClass(), false, elementType.get());
      }
    }
    return null;
  }

  @Nullable
  public PyType getReturnStatementType(TypeEvalContext typeEvalContext) {
    ReturnVisitor visitor = new ReturnVisitor(this, typeEvalContext);
    final PyStatementList statements = getStatementList();
    if (statements != null) {
      statements.accept(visitor);
      if (isGeneratedStub() && !visitor.myHasReturns) {
        if (PyNames.INIT.equals(getName())) {
          return PyNoneType.INSTANCE;
        }
        return null;
      }
    }
    return visitor.result();
  }

  public PyFunction asMethod() {
    if (getContainingClass() != null) {
      return this;
    }
    else {
      return null;
    }
  }

  public PyType getReturnTypeFromDocString() {
    final String value = getDocStringValue();
    String typeName = value != null ? extractReturnType(value) : null;
    return PyTypeParser.getTypeByName(this, typeName);
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
    if (statementList == null) {
      return null;
    }
    return extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
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
              return PyUtil.strValue(callExpression.getArguments()[0]);
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
    final PyStringLiteralExpression docStringExpression = getDocStringExpression();
    return PyUtil.strValue(docStringExpression);
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
  private static String extractReturnType(String docString) {
    final List<String> lines = StringUtil.split(docString, "\n");
    while (lines.size() > 0 && lines.get(0).trim().length() == 0) {
      lines.remove(0);
    }
    if (lines.size() > 1 && lines.get(1).trim().length() == 0) {
      String firstLine = lines.get(0);
      int pos = firstLine.lastIndexOf("->");
      if (pos >= 0) {
        return firstLine.substring(pos + 2).trim();
      }
    }

    StructuredDocString epydocString = StructuredDocString.parse(docString);
    return epydocString != null ? epydocString.getReturnType() : null;
  }

  private static class ReturnVisitor extends PyRecursiveElementVisitor {
    private final PyFunction myFunction;
    private final TypeEvalContext myContext;
    private PyType myResult = null;
    private boolean myHasReturns = false;

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

    PyType result() {
      return myHasReturns ? myResult : PyNoneType.INSTANCE;
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

  public void delete() throws IncorrectOperationException {
    ASTNode node = getNode();
    node.getTreeParent().removeChild(node);
  }

  public PyStringLiteralExpression getDocStringExpression() {
    final PyStatementList stmtList = getStatementList();
    return stmtList != null ? PythonDocStringFinder.find(stmtList) : null;
  }

  protected String getElementLocation() {
    final PyClass containingClass = getContainingClass();
    if (containingClass != null) {
      return "(" + containingClass.getName() + " in " + getPackageForFile(getContainingFile()) + ")";
    }
    return super.getElementLocation();
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return Collections.<PyElement>singleton(this);
  }

  public PyElement getElementNamed(final String the_name) {
    return the_name.equals(getName()) ? this : null;
  }

  public boolean mustResolveOutside() {
    return false;
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
      return containingClass.findPropertyByFunction(this);
    }
    return null;
  }

  @Override
  public PyAnnotation getAnnotation() {
    return findChildByClass(PyAnnotation.class);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(this, ScopeOwner.class);
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
    String deconame = getClassOrStaticMethodDecorator();
    if (PyNames.CLASSMETHOD.equals(deconame)) {
      return CLASSMETHOD;
    }
    else if (PyNames.STATICMETHOD.equals(deconame)) {
      return STATICMETHOD;
    }
    // implicit staticmethod __new__
    PyClass cls = getContainingClass();
    if (cls != null && PyNames.NEW.equals(getName()) && cls.isNewStyleClass()) {
      return STATICMETHOD;
    }
    //
    if (getStub() != null) {
      return getWrappersFromStub();
    }
    String func_name = getName();
    if (func_name != null) {
      PyAssignmentStatement assignment = PsiTreeUtil.getNextSiblingOfType(this, PyAssignmentStatement.class);
      if (assignment != null) {
        for (Pair<PyExpression, PyExpression> pair : assignment.getTargetsToValuesMapping()) {
          PyExpression value = pair.getSecond();
          if (value instanceof PyCallExpression) {
            PyExpression target = pair.getFirst();
            if (target instanceof PyTargetExpression && func_name.equals(target.getName())) {
              Pair<String, PyFunction> interpreted = interpretAsModifierWrappingCall((PyCallExpression)value, this);
              if (interpreted != null) {
                PyFunction original = interpreted.getSecond();
                if (original == this) {
                  String wrapper_name = interpreted.getFirst();
                  if (PyNames.CLASSMETHOD.equals(wrapper_name)) {
                    return CLASSMETHOD;
                  }
                  else if (PyNames.STATICMETHOD.equals(wrapper_name)) {
                    return STATICMETHOD;
                  }
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private Modifier getWrappersFromStub() {
    final StubElement parentStub = getStub().getParentStub();
    final List childrenStubs = parentStub.getChildrenStubs();
    int index = childrenStubs.indexOf(getStub());
    if (index >= 0 && index < childrenStubs.size() - 1) {
      StubElement nextStub = (StubElement) childrenStubs.get(index+1);
      if (nextStub instanceof PyTargetExpressionStub) {
        final PyTargetExpressionStub targetExpressionStub = (PyTargetExpressionStub)nextStub;
        if (targetExpressionStub.getInitializerType() == PyTargetExpressionStub.InitializerType.CallExpression) {
          final PyQualifiedName qualifiedName = targetExpressionStub.getInitializer();
          if (PyQualifiedName.fromComponents(PyNames.CLASSMETHOD).equals(qualifiedName)) {
            return CLASSMETHOD;
          }
          if (PyQualifiedName.fromComponents(PyNames.STATICMETHOD).equals(qualifiedName)) {
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
          for(PyKnownDecoratorProvider provider: PyUtil.KnownDecoratorProviderHolder.KNOWN_DECORATOR_PROVIDERS) {
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
}
