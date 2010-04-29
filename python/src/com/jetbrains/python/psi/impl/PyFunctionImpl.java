package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.codeInsight.controlflow.PyControlFlowBuilder;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.impl.ScopeImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.SingleIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

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
    return Icons.METHOD_ICON;
  }

  @Nullable
  public ASTNode getNameNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @NotNull
  public PyParameterList getParameterList() {
    return getRequiredStubOrPsiChild(PyElementTypes.PARAMETER_LIST); 
  }

  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
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
        return (PyClass) pparent;
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
  public PyType getReturnType() {
    final PyStringLiteralExpression docString = getDocStringExpression();
    if (docString != null) {
      PyType result = getReturnTypeFromDocString(docString.getStringValue());
      if (result != null) {
        return result;
      }
    }

    ReturnVisitor visitor = new ReturnVisitor();
    getStatementList().accept(visitor);
    if (isGeneratedStub() && !visitor.myHasReturns) {
      return null;
    }
    return visitor.result();
  }

  private boolean isGeneratedStub() {
    VirtualFile vFile = getContainingFile().getVirtualFile();
    if (vFile != null) {
      vFile = vFile.getParent();
      if (vFile != null) {
        vFile = vFile.getParent();
        if (vFile != null && vFile.getName().equals("python_stubs")) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private PyType getReturnTypeFromDocString(String docString) {
    final List<String> lines = StringUtil.split(docString, "\n");
    while (lines.size() > 0 && lines.get(0).trim().length() == 0) {
      lines.remove(0);
    }
    if (lines.size() > 1 && lines.get(1).trim().length() == 0) {
      String firstLine = lines.get(0);
      int pos = firstLine.lastIndexOf("->");
      if (pos >= 0) {
        String returnType = firstLine.substring(pos+2).trim();
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(this);

        if (returnType.equals("string")) {
          return builtinCache.getStrType();
        }
        if (returnType.equals("file object")) {
          return builtinCache.getObjectType("file");
        }
        if (returnType.equals("dictionary")) {
          return builtinCache.getObjectType("dict");
        }
        if (returnType.startsWith("list of")) {
          return builtinCache.getObjectType("list");
        }
        if (returnType.equals("integer")) {
          return builtinCache.getIntType();
        }
        final PyClassType type = builtinCache.getObjectType(returnType);
        if (type != null) {
          return type;
        }
      }
    }
    return null;
  }

  private static class ReturnVisitor extends PyRecursiveElementVisitor {
    private final TypeEvalContext myContext = TypeEvalContext.slow();
    private PyType myResult = null;
    private boolean myHasReturns = false;

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
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
                                     @NotNull PsiElement place)
  {
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
    return PythonDocStringFinder.find(getStatementList());
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
    return new SingleIterable<PyElement>(this);
  }

  public PyElement getElementNamed(final String the_name) {
    return the_name.equals(getName())? this : null;
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
    if (myControlFlowRef != null){
      myControlFlowRef.clear();
    }
    if (myScopeRef != null){
      myScopeRef.clear();
    }
  }

  private SoftReference<ControlFlow> myControlFlowRef;

  @NotNull
  public ControlFlow getControlFlow() {
    ControlFlow flow = getRefValue(myControlFlowRef);
    if (flow == null) {
      flow = new PyControlFlowBuilder().buildControlFlow(this);
      myControlFlowRef = new SoftReference<ControlFlow>(flow);
    }
    return flow;
  }

  private SoftReference<Scope> myScopeRef;

  @NotNull
  public Scope getScope() {
    Scope scope = getRefValue(myScopeRef);
    if (scope == null) {
      scope = new ScopeImpl(this);
      myScopeRef = new SoftReference<Scope>(scope);
    }
    return scope;
  }

  @Nullable
  private static<T> T getRefValue(final SoftReference<T> reference){
    return reference != null ? reference.get() : null;
  }
}
