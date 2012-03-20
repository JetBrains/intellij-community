package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyParameterListStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author yole
 */
public class PyParameterListImpl extends PyBaseElementImpl<PyParameterListStub> implements PyParameterList {
  public PyParameterListImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyParameterListImpl(final PyParameterListStub stub) {
    this(stub, PyElementTypes.PARAMETER_LIST);
  }

  public PyParameterListImpl(final PyParameterListStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParameterList(this);
  }

  public PyParameter[] getParameters() {
    return getStubOrPsiChildren(PyElementTypes.PARAMETERS, new PyParameter[0]);
  }

  public void addParameter(final PyNamedParameter param) {
    PsiElement paren = getLastChild();
    if (paren != null && ")".equals(paren.getText())) {
      ASTNode beforeWhat = paren.getNode(); // the closing paren will be this
      PyParameter[] params = getParameters();
      PyUtil.addListNode(this, param, beforeWhat, true, params.length == 0);
    }
  }

  public boolean hasPositionalContainer() {
    for (PyParameter parameter: getParameters()) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isPositionalContainer()) {
        return true;
      }
    }
    return false;
  }

  public boolean hasKeywordContainer() {
    for (PyParameter parameter: getParameters()) {
      if (parameter instanceof PyNamedParameter && ((PyNamedParameter) parameter).isKeywordContainer()) {
        return true;
      }
    }
    return false;
  }

  public boolean isCompatibleTo(@NotNull PyParameterList another) {
    PyParameter[] parameters = getParameters();
    final PyParameter[] anotherParameters = another.getParameters();
    final int parametersLength = parameters.length;
    final int anotherParametersLength = anotherParameters.length;
    if (parametersLength == anotherParametersLength) {
      if (hasPositionalContainer() == another.hasPositionalContainer() && hasKeywordContainer() == another.hasKeywordContainer()) {
        return true;
      }
    }

    int i = 0;
    int j = 0;
    while (i < parametersLength && j < anotherParametersLength) {
      PyParameter parameter = parameters[i];
      PyParameter anotherParameter = anotherParameters[j];
      if (parameter instanceof PyNamedParameter && anotherParameter instanceof PyNamedParameter) {
        PyNamedParameter namedParameter = (PyNamedParameter)parameter;
        PyNamedParameter anotherNamedParameter = (PyNamedParameter)anotherParameter;

        if (namedParameter.isPositionalContainer()) {
          while (j < anotherParametersLength
                 && !anotherNamedParameter.isPositionalContainer()
                 && !anotherNamedParameter.isKeywordContainer()) {
            anotherParameter = anotherParameters[j++];
            anotherNamedParameter = (PyNamedParameter) anotherParameter;
          }
          ++i;
          continue;
        }

        if (anotherNamedParameter.isPositionalContainer()) {
          while (i < parametersLength
                 && !namedParameter.isPositionalContainer()
                 && !namedParameter.isKeywordContainer()) {
            parameter = parameters[i++];
            namedParameter = (PyNamedParameter) parameter;
          }
          ++j;
          continue;
        }

        if (namedParameter.isKeywordContainer() || anotherNamedParameter.isKeywordContainer()) {
          break;
        }
      }

      // both are simple parameters
      ++i;
      ++j;
    }

    if (i < parametersLength) {
      if (parameters[i] instanceof PyNamedParameter) {
        final PyNamedParameter nextParameter = (PyNamedParameter)parameters[i];
        if (nextParameter.isKeywordContainer() || nextParameter.isPositionalContainer()) {
          ++i;
        }
        while (nextParameter.isKeywordContainer() && j<anotherParametersLength && anotherParameters[j].hasDefaultValue()) {
          j++;
        }
      }
    }

    if (j < anotherParametersLength) {
      if (anotherParameters[j] instanceof PyNamedParameter) {
        final PyNamedParameter nextParameter = (PyNamedParameter)anotherParameters[j];
        if (nextParameter.isKeywordContainer() || nextParameter.isPositionalContainer()) {
          ++j;
        }
        while (nextParameter.isKeywordContainer() && i<parametersLength && parameters[i].hasDefaultValue()) {
          i++;
        }
      }
    }
    return (i >= parametersLength) && (j >= anotherParametersLength);
    //
    //if (weHaveStarred && parameters.length - 1 <= anotherParameters.length) {
    //  if (weHaveDoubleStarred == anotherHasDoubleStarred) {
    //    return true;
    //  }
    //}
    //if ((anotherHasDoubleStarred && parameters.length == anotherParameters.length - 1)
    //  || (weHaveDoubleStarred && parameters.length == anotherParameters.length + 1)) {
    //  return true;
    //}
    //return false;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new ArrayList<PyElement>(ParamHelper.collectNamedParameters(this));
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  @Override
  @Nullable
  public PyNamedParameter findParameterByName(@NotNull final String name) {
    final Ref<PyNamedParameter> result = new Ref<PyNamedParameter>();
    ParamHelper.walkDownParamArray(getParameters(), new ParamHelper.ParamVisitor() {
      @Override
      public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
        if (name.equals(param.getName())) {
          result.set(param);
        }
      }
    });
    return result.get();
  }

  public boolean mustResolveOutside() {
    return true;
  }

  public String getPresentableText(final boolean includeDefaultValue) {
    final StringBuilder target = new StringBuilder();
    final String COMMA = ", ";
    target.append("(");
    ParamHelper.walkDownParamArray(
      getParameters(),
      new ParamHelper.ParamWalker() {
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          target.append("(");
        }

        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          target.append(")");
          if (!last) target.append(COMMA);
        }

        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          target.append(param.getRepr(includeDefaultValue));
          if (!last) target.append(COMMA);
        }

        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          target.append('*');
          if (!last) target.append(COMMA);
        }
      }
    );
    target.append(")");
    return target.toString();
  }

  @Nullable
  @Override
  public PyFunction getContainingFunction() {
    final PsiElement parent = getStubOrPsiParent();
    return parent instanceof PyFunction ? (PyFunction) parent : null;
  }
}
