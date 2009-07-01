/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDosStringFinder;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 23:01:25
 * To change this template use File | Settings | File Templates.
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

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode nameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), name);
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
      final PyParameter[] params = getParameterList().getParameters();
      for (PyParameter param : params) {
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
    return PythonDosStringFinder.find(getStatementList());
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

  /**
   * Shows function name with parameter list.
   * <br/><i>Note that nested tuple parameters are flattened!</i>
   * Needs to be fixed on PSI and stub level first. 
   * @return the presentation
   */
  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        @NonNls StringBuilder sb = new StringBuilder();
        /* too much detail hurts
        sb.append(getElementLocation()).append("\n");
        PyDecoratorList deco_list = getDecoratorList();
        if (deco_list != null) {
          for (PyDecorator deco : deco_list.getDecorators()) {
            sb.append("@").append(PyUtil.getReadableRepr(deco.getCallee(), true));
            if (deco.getArgumentList().getArguments() != null) sb.append("(...)");  // XXX fails to detect absent arglist!
            sb.append("\n");
          }
        }
        */
        final String name = getName();
        sb.append(name != null ? name : "<none>");
        PyParameter[] params = getParameterList().getParameters();
        String[] par_names = new String[params.length];
        for (int i = 0; i < params.length; i += 1) par_names[i] = (params[i].getRepr(true));
        sb.append("(");
        PyUtil.joinSubarray(par_names, 0, par_names.length, ", ", sb);
        sb.append(")");
        return sb.toString();
      }

      public String getLocationString() {
        return getElementLocation();
      }

      public Icon getIcon(final boolean open) {
        return PyFunctionImpl.this.getIcon(0);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }
}
