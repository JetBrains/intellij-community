package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.documentation.StructuredDocString;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
      final ASTNode nameElement = PyElementGenerator.getInstance(getProject()).createNameIdentifier(name);
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

  public PyType getType(@NotNull TypeEvalContext context) {
    final PsiElement parent = getStubOrPsiParent();
    if (parent instanceof PyParameterList) {
      PyParameterList parameterList = (PyParameterList)parent;
      final PyParameter[] params = parameterList.getParameters();
      PyFunction func = parameterList.getContainingFunction();
      if (func != null) {
        final PyFunction.Modifier modifier = func.getModifier();
        if (params [0] == this && modifier != PyFunction.Modifier.STATICMETHOD) {
          // must be 'self' or 'cls'
          final PyClass containingClass = func.getContainingClass();
          if (containingClass != null) {
            PyType initType = null;
            final PyFunction init = containingClass.findInitOrNew(true);
            if (init != null) {
              initType = init.getReturnType(context, null);
              if (init.getContainingClass() != containingClass) {
                if (initType instanceof PyCollectionType) {
                  final PyType elementType = ((PyCollectionType)initType).getElementType(context);
                  return new PyCollectionTypeImpl(containingClass, false, elementType);
                }
              }
            }
            else {
              final PyStdlibTypeProvider stdlib = PyStdlibTypeProvider.getInstance();
              if (stdlib != null) {
                initType = stdlib.getConstructorType(containingClass);
              }
            }
            if (initType != null && !(initType instanceof PyNoneType)) {
              return initType;
            }
            return new PyClassTypeImpl(containingClass, modifier == PyFunction.Modifier.CLASSMETHOD);
          }
        }
        if (isKeywordContainer()) {
          return PyBuiltinCache.getInstance(this).getDictType();
        }
        if (isPositionalContainer()) {
          return PyBuiltinCache.getInstance(this).getTupleType();
        }
        PyAnnotation anno = getAnnotation();
        if (anno != null) {
          final PyClass pyClass = anno.resolveToClass();
          if (pyClass != null) {
            return new PyClassTypeImpl(pyClass, false);
          }
        }

        String docString = func.getDocStringValue();
        if (PyNames.INIT.equals(func.getName()) && docString == null) {
          PyClass pyClass = func.getContainingClass();
          if (pyClass != null)
            docString = pyClass.getDocStringValue();
        }
        StructuredDocString epydocString = StructuredDocString.parse(docString);
        if (epydocString != null) {
          String typeName = epydocString.getParamType(getName());
          if (typeName != null) {
            return PyTypeParser.getTypeByName(this, typeName);
          }
        }

        for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
          PyType result = provider.getParameterType(this, func, context);
          if (result != null) return result;
        }
      }
    }
    return null;
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
}
