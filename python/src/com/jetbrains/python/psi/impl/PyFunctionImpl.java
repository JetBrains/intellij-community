package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
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
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Implements PyFunction.
 */
public class PyFunctionImpl extends PyPresentableElementImpl<PyFunctionStub> implements PyFunction {
  public PyFunctionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFunctionImpl(final PyFunctionStub stub) {
    super(stub, PyElementTypes.FUNCTION_DECLARATION);
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
    getNode().replaceChild(getNameNode(), nameElement);
    return this;
  }

  @Override
  public Icon getIcon(int flags) {
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
  public PyType getReturnType(TypeEvalContext typeEvalContext, @Nullable PyReferenceExpression callSite) {
    PyAnnotation anno = getAnnotation();
    if (anno != null) {
      PyClass pyClass = anno.resolveToClass();
      if (pyClass != null) {
        return new PyClassType(pyClass, false);
      }
    }
    for(PyTypeProvider typeProvider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType returnType = typeProvider.getReturnType(this, callSite, typeEvalContext);
      if (returnType != null) {
        return returnType;
      }
    }
    final PyType docStringType = getReturnTypeFromDocString();
    if (docStringType != null) {
      return docStringType;
    }    
    if (typeEvalContext.allowReturnTypes()) {
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
    PyType elementType = null;
    final PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
    final PyClass listClass = cache.getClass("list");
    final PyStatementList statements = getStatementList();
    final Set<PyType> types = new HashSet<PyType>();
    if (statements != null && listClass != null) {
      statements.accept(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyYieldExpression(PyYieldExpression node) {
          PyType t = node.getType(context);
          if (t != null) {
            types.add(t);
          }
          else {
            types.add(cache.getObjectType());
          }
        }
      });
      final int n = types.size();
      if (n == 1) {
        elementType = types.iterator().next();
      }
      else if (n > 0) {
        elementType = new PyUnionType(types);
      }
    }
    if (elementType != null) {
       return new PyCollectionTypeImpl(listClass, false, elementType);
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
    String typeName;
    final PyFunctionStub stub = getStub();
    if (stub != null) {
      typeName = stub.getReturnTypeFromDocString();
    }
    else {
      typeName = extractDocStringReturnType();
    }
    return PyTypeParser.getTypeByName(this, typeName);
  }

  @Override
  public String getDeprecationMessage() {
    PyFunctionStub stub = getStub();
    if (stub != null) {
      return stub.getDeprecationMessage();
    }
    return extractDeprecationMessage();
  }

  public String extractDeprecationMessage() {
    PyStatementList statementList = getStatementList();
    if (statementList == null) {
      return null;
    }
    return extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
  }

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
              return PyUtil.strValue(callExpression.getArguments() [0]);
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public String extractDocStringReturnType() {
    final PyStringLiteralExpression docString = getDocStringExpression();
    if (docString != null) {
      return extractReturnType(docString.getStringValue());
    }
    return null;
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
    return epydocString.getReturnType();
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
          if (myResult == null) {
            myResult = returnType;
          }
          else {
            if (returnType != null) {
              myResult = PyUnionType.union(myResult, returnType);
            }
          }
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

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    /*
    if (lastParent != null && lastParent.getParent() == this) {
      final PyNamedParameter[] params = getParameterList().getParameters();
      for (PyNamedParameter param : params) {
        if (!processor.execute(param, substitutor)) return false;
      }
    }
    */
    return processor.execute(this, substitutor);
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
    final String name = getName();
    if (containingClass != null && name != null) {
      // TODO find property which uses property call, rather than annotation (function name will be different in that case)
      return containingClass.findProperty(name); 
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
}
