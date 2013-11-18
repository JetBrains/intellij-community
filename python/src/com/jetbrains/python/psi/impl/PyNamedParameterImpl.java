/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyNamedParameterImpl extends PyPresentableElementImpl<PyNamedParameterStub> implements PyNamedParameter {
  public PyNamedParameterImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyNamedParameterImpl(final PyNamedParameterStub stub) {
    this(stub, PyElementTypes.NAMED_PARAMETER);
  }

  public PyNamedParameterImpl(final PyNamedParameterStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Nullable
  @Override
  public String getName() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      ASTNode node = getNameIdentifierNode();
      return node != null ? node.getText() : null;
    }
  }

  @Override
  public int getTextOffset() {
    ASTNode node = getNameIdentifierNode();
    return node == null ? super.getTextOffset() : node.getTextRange().getStartOffset();
  }

  @Nullable
  protected ASTNode getNameIdentifierNode() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  public PsiElement getNameIdentifier() {
    final ASTNode node = getNameIdentifierNode();
    return node == null ? null : node.getPsi();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode oldNameIdentifier = getNameIdentifierNode();
    if (oldNameIdentifier != null) {
      final ASTNode nameElement = PyUtil.createNewName(this, name);
      getNode().replaceChild(oldNameIdentifier, nameElement);
    }
    return this;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyNamedParameter(this);
  }

  public boolean isPositionalContainer() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.isPositionalContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.MULT) != null;
    }
  }

  public boolean isKeywordContainer() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.isKeywordContainer();
    }
    else {
      return getNode().findChildByType(PyTokenTypes.EXP) != null;
    }
  }

  @Nullable
  public PyExpression getDefaultValue() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null && !stub.hasDefaultValue()) {
      return null;
    }
    ASTNode[] nodes = getNode().getChildren(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    if (nodes.length > 0) {
      return (PyExpression)nodes[0].getPsi();
    }
    return null;
  }

  public boolean hasDefaultValue() {
    final PyNamedParameterStub stub = getStub();
    if (stub != null) {
      return stub.hasDefaultValue();
    }
    return getDefaultValue() != null;
  }

  @NotNull
  public String getRepr(boolean includeDefaultValue) {
    StringBuilder sb = new StringBuilder();
    if (isPositionalContainer()) sb.append("*");
    else if (isKeywordContainer()) sb.append("**");
    sb.append(getName());
    if (includeDefaultValue) {
      PyExpression default_v = getDefaultValue();
      if (default_v != null) sb.append("=").append(PyUtil.getReadableRepr(default_v, true));
    }
    return sb.toString();
  }

  @Override
  public PyAnnotation getAnnotation() {
    return getStubOrPsiChild(PyElementTypes.ANNOTATION);
  }

  public Icon getIcon(final int flags) {
    return PlatformIcons.PARAMETER_ICON;
  }

  public PyNamedParameter getAsNamed() {
    return this;
  }

  public PyTupleParameter getAsTuple() {
    return null; // we're not a tuple
  }

  public PyType getType(@NotNull final TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PsiElement parent = getStubOrPsiParent();
    if (parent instanceof PyParameterList) {
      PyParameterList parameterList = (PyParameterList)parent;
      PyFunction func = parameterList.getContainingFunction();
      if (func != null) {
        PyAnnotation anno = getAnnotation();
        if (anno != null) {
          final PyClass pyClass = anno.resolveToClass();
          if (pyClass != null) {
            return new PyClassTypeImpl(pyClass, false);
          }
        }
        StructuredDocString docString = func.getStructuredDocString();
        if (PyNames.INIT.equals(func.getName()) && docString == null) {
          PyClass pyClass = func.getContainingClass();
          if (pyClass != null) {
            docString = pyClass.getStructuredDocString();
          }
        }
        if (docString != null) {
          String typeName = docString.getParamType(getName());
          if (typeName != null) {
            return PyTypeParser.getTypeByName(this, typeName);
          }
        }
        if (isSelf()) {
          // must be 'self' or 'cls'
          final PyClass containingClass = func.getContainingClass();
          if (containingClass != null) {
            PyType initType = null;
            final PyFunction init = containingClass.findInitOrNew(true);
            if (init != null && init != func) {
              initType = init.getReturnType(context, null);
              if (init.getContainingClass() != containingClass) {
                if (initType instanceof PyCollectionType) {
                  final PyType elementType = ((PyCollectionType)initType).getElementType(context);
                  return new PyCollectionTypeImpl(containingClass, false, elementType);
                }
              }
            }
            if (initType != null && !(initType instanceof PyNoneType)) {
              return initType;
            }
            final PyFunction.Modifier modifier = func.getModifier();
            return new PyClassTypeImpl(containingClass, modifier == PyFunction.Modifier.CLASSMETHOD);
          }
        }
        if (isKeywordContainer()) {
          return PyBuiltinCache.getInstance(this).getDictType();
        }
        if (isPositionalContainer()) {
          return PyBuiltinCache.getInstance(this).getTupleType();
        }
        for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
          PyType result = provider.getParameterType(this, func, context);
          if (result != null) return result;
        }
        if (context.maySwitchToAST(this)) {
          final PyExpression defaultValue = getDefaultValue();
          if (defaultValue != null) {
            final PyType type = context.getType(defaultValue);
            if (type != null && !(type instanceof PyNoneType)) {
              return type;
            }
          }
        }
        // Guess the type from file-local usages
        if (context.allowLocalUsages(this)) {
          final List<PyType> types = new ArrayList<PyType>();
          processLocalCalls(func, new Processor<PyCallExpression>() {
            @Override
            public boolean process(@NotNull PyCallExpression call) {
              final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
              final CallArgumentsMapping mapping = call.getArgumentList().analyzeCall(resolveContext);
              for (Map.Entry<PyExpression, PyNamedParameter> entry : mapping.getPlainMappedParams().entrySet()) {
                if (entry.getValue() == PyNamedParameterImpl.this) {
                  final PyExpression argument = entry.getKey();
                  if (argument != null) {
                    final PyType type = context.getType(argument);
                    if (type != null) {
                      types.add(type);
                      return true;
                    }
                  }
                }
              }
              return true;
            }
          });
          if (!types.isEmpty()) {
            return PyUnionType.createWeakType(PyUnionType.union(types));
          }
        }
      }
    }
    return null;
  }

  private static void processLocalCalls(@NotNull PyFunction function, @NotNull Processor<PyCallExpression> processor) {
    final PsiFile file = function.getContainingFile();
    final String name = function.getName();
    if (file != null && name != null) {
      // Text search is faster than ReferencesSearch in LocalSearchScope
      final String text = file.getText();
      for (int pos = text.indexOf(name); pos != -1; pos = text.indexOf(name, pos + 1)) {
        final PsiReference ref = file.findReferenceAt(pos);
        if (ref != null && ref.isReferenceTo(function)) {
          final PyCallExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(pos), PyCallExpression.class);
          if (expr != null && !processor.process(expr)) {
            return;
          }
        }
      }
    }
  }

  @Override
  public String toString() {
    return super.toString() + "('" + getName() + "')";
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    PyFunction func = PsiTreeUtil.getParentOfType(this, PyFunction.class);
    if (func != null) {
      return new LocalSearchScope(func);
    }
    return new LocalSearchScope(getContainingFile());
  }

  @Override
  public boolean isSelf() {
    if (isPositionalContainer() || isKeywordContainer()) {
      return false;
    }
    PyFunction function = getStubOrPsiParentOfType(PyFunction.class);
    if (function == null) {
      return false;
    }
    final PyClass cls = function.getContainingClass();
    final PyParameter[] parameters = function.getParameterList().getParameters();
    if (cls != null && parameters.length > 0 && parameters[0] == this) {
      if (PyNames.NEW.equals(function.getName())) {
        return true;
      }
      final PyFunction.Modifier modifier = function.getModifier();
      if (modifier != PyFunction.Modifier.STATICMETHOD) {
        return true;
      }
    }
    return false;
  }
}
