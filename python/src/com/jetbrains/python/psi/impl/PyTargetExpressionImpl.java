package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.documentation.StructuredDocString;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.impl.references.PyTargetReference;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;

/**
 * @author yole
 */
public class PyTargetExpressionImpl extends PyPresentableElementImpl<PyTargetExpressionStub> implements PyTargetExpression {
  public PyTargetExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyTargetExpressionImpl(final PyTargetExpressionStub stub) {
    super(stub, PyElementTypes.TARGET_EXPRESSION);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTargetExpression(this);
  }

  @Nullable
  @Override
  public String getName() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    ASTNode node = getNameElement();
    return node != null ? node.getText() : null;
  }

  @Override
  public int getTextOffset() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getStartOffset() : getTextRange().getStartOffset();
  }

  @Nullable
  public ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  public PsiElement getNameIdentifier() {
    final ASTNode nameElement = getNameElement();
    return nameElement == null ? null : nameElement.getPsi();
  }

  public String getReferencedName() {
    return getName();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final ASTNode oldNameElement = getNameElement();
    if (oldNameElement != null) {
      final ASTNode nameElement = PyElementGenerator.getInstance(getProject()).createNameIdentifier(name);
      getNode().replaceChild(oldNameElement, nameElement);
    }
    return this;
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    if (!TypeEvalStack.mayEvaluate(this)) {
      return null;
    }
    try {
      if (PyNames.ALL.equals(getName())) {
        // no type for __all__, to avoid unresolved reference errors for expressions where a qualifier is a name
        // imported via __all__
        return null;
      }
      if (!context.maySwitchToAST(this)) {
        return null;
      }
      PyType type = getTypeFromDocString(this);
      if (type != null) {
        return type;
      }
      type = getTypeFromComment(this);
      if (type != null) {
        return type;
      }
      final PsiElement parent = getParent();
      if (parent instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)parent;
        final PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue != null) {
          if (assignedValue instanceof PyReferenceExpressionImpl) {
            final PyReferenceExpressionImpl refex = (PyReferenceExpressionImpl)assignedValue;
            PyType maybe_type = PyUtil.getSpecialAttributeType(refex, context);
            if (maybe_type != null) return maybe_type;
            final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
            final ResolveResult[] resolveResult = refex.getReference(resolveContext).multiResolve(false);
            if (resolveResult.length == 1) {
              PsiElement target = resolveResult[0].getElement();
              if (target == this || target == null) {
                return null;  // fix SOE on "a = a"
              }
              final PyType typeFromTarget = PyReferenceExpressionImpl.getTypeFromTarget(target, context, refex);
              if (target instanceof PyTargetExpression && typeFromTarget instanceof PyNoneType) {
                // this usually means that the variable is initialized to a non-None value somewhere else where we haven't looked
                return null;
              }
              Ref<PyType> typeOfProperty = refex.getTypeOfProperty(context);
              if (typeOfProperty != null) {
                return typeOfProperty.get();
              }
              return typeFromTarget;
            }
          }
          if (assignedValue instanceof PyYieldExpression) {
            return null;
          }
          return context.getType(assignedValue);
        }
      }
      if (parent instanceof PyTupleExpression) {
        final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(parent, PyAssignmentStatement.class);
        if (assignment != null) {
          final PyExpression value = assignment.getAssignedValue();
          if (value != null) {
            final PyType assignedType = value.getType(context);
            if (assignedType instanceof PyTupleType) {
              final PyType t = getTypeFromTupleAssignment((PyTupleExpression)parent, (PyTupleType)assignedType);
              if (t != null) {
                return t;
              }
            }
          }
        }
      }
      if (parent instanceof PyWithItem) {
        final PyWithItem item = (PyWithItem)parent;
        final PyExpression expression = item.getExpression();
        if (expression != null) {
          final PyType exprType = expression.getType(context);
          if (exprType instanceof PyClassType) {
            final PyClass cls = ((PyClassType)exprType).getPyClass();
            if (cls != null) {
              final PyFunction enter = cls.findMethodByName(PyNames.ENTER, true);
              if (enter != null) {
                return enter.getReturnType(context, null);
              }
            }
          }
        }
        return null;
      }
      PyType iterType = getTypeFromIteration(context);
      if (iterType != null) {
        return iterType;
      }
      PyType excType = getTypeFromExcept();
      if (excType != null) {
        return excType;
      }
      return null;
    }
    finally {
      TypeEvalStack.evaluated(this);
    }
  }

  @Nullable
  public static PyType getTypeFromDocString(PyTargetExpressionImpl targetExpression) {
    final String docString = PyUtil.strValue(PyUtil.getAttributeDocString(targetExpression));
    if (docString != null) {
      StructuredDocString structuredDocString = StructuredDocString.parse(docString);
      if (structuredDocString != null) {
        String typeName = structuredDocString.getParamType(null);
        if (typeName == null) {
          typeName = structuredDocString.getParamType(targetExpression.getName());
        }
        if (typeName != null) {
          return PyTypeParser.getTypeByName(targetExpression, typeName);
        }
      }
    }
    return null;
  }

  @Nullable
  public static PyType getTypeFromComment(PyTargetExpressionImpl targetExpression) {
    String docComment = PyUtil.getAttributeDocComment(targetExpression);
    if (docComment != null) {
      StructuredDocString structuredDocString = StructuredDocString.parse(docComment);
      if (structuredDocString != null) {
        String typeName = structuredDocString.getParamType(null);
        if (typeName == null) {
          typeName = structuredDocString.getParamType(targetExpression.getName());
        }
        if (typeName != null) {
          return PyTypeParser.getTypeByName(targetExpression, typeName);
        }
      }
    }
    return null;
  }

  @Nullable
  private PyType getTypeFromTupleAssignment(@NotNull PyTupleExpression tuple, @NotNull PyTupleType tupleType) {
    if (tuple.getElements().length == tupleType.getElementCount()) {
      int selfIndex = ArrayUtil.indexOf(tuple.getElements(), this);
      return tupleType.getElementType(selfIndex);
    }
    return null;
  }

  @Nullable
  private PyType getTypeFromIteration(@NotNull TypeEvalContext context) {
    PyExpression target = null;
    PyExpression source = null;
    final PyForPart forPart = PsiTreeUtil.getParentOfType(this, PyForPart.class);
    if (forPart != null) {
      final PyExpression expr = forPart.getTarget();
      if (PsiTreeUtil.isAncestor(expr, this, false)) {
        target = expr;
        source = forPart.getSource();
      }
    }
    final PyComprehensionElement comprh = PsiTreeUtil.getParentOfType(this, PyComprehensionElement.class);
    if (comprh != null) {
      for (ComprhForComponent c : comprh.getForComponents()) {
        final PyExpression expr = c.getIteratorVariable();
        if (PsiTreeUtil.isAncestor(expr, this, false)) {
          target = expr;
          source = c.getIteratedList();
        }
      }
    }
    if (source != null && target != null) {
      PyType type = null;
      final PyType sourceType = source.getType(context);
      if (sourceType instanceof PyCollectionType) {
        type = ((PyCollectionType)sourceType).getElementType(context);
      }
      else if (sourceType instanceof PyClassType) {
        final PyClass pyClass = ((PyClassType)sourceType).getPyClass();
        if (pyClass != null) {
          for (PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
            final PyType iterType = provider.getIterationType(pyClass);
            if (iterType != null) {
              type = iterType;
              break;
            }
          }
          if (PyABCUtil.isSubclass(pyClass, PyNames.ITERATOR)) {
            final PyFunction iter = pyClass.findMethodByName(PyNames.ITER, true);
            PyType iterMethodType = null;
            if (iter != null) {
              iterMethodType = getContextSensitiveType(iter, context, source);
            }
            if (iterMethodType instanceof PyCollectionType) {
              final PyCollectionType collectionType = (PyCollectionType)iterMethodType;
              type = collectionType.getElementType(context);
            }
            if (type == null) {
              PyFunction next = pyClass.findMethodByName(PyNames.NEXT, true);
              if (next == null) {
                next = pyClass.findMethodByName(PyNames.DUNDER_NEXT, true);
              }
              if (next != null) {
                type = getContextSensitiveType(next, context, source);
              }
            }
          }
        }
      }
      final PsiElement parent = getParent();
      if (type instanceof PyTupleType && parent instanceof PyTupleExpression) {
        return getTypeFromTupleAssignment((PyTupleExpression)parent, (PyTupleType)type);
      }
      if (target == this && type != null) {
        return type;
      }
    }
    return null;
  }

  @Nullable
  private static PyType getContextSensitiveType(@NotNull PyFunction function, @NotNull TypeEvalContext context,
                                                @NotNull PyExpression source) {
    if (function instanceof PyFunctionImpl) {
      return ((PyFunctionImpl)function).getReturnType(context, source, new HashMap<PyExpression, PyNamedParameter>());
    }
    return function.getReturnType(context, null);
  }

  @Nullable
  private PyType getTypeFromExcept() {
    PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(this, PyExceptPart.class);
    if (exceptPart == null || exceptPart.getTarget() != this) {
      return null;
    }
    final PyExpression exceptClass = exceptPart.getExceptClass();
    if (exceptClass instanceof PyReferenceExpression) {
      final PsiElement element = ((PyReferenceExpression)exceptClass).getReference().resolve();
      if (element instanceof PyClass) {
        return new PyClassType((PyClass) element, false);
      }
    }
    return null;
  }

  public PyExpression getQualifier() {
    ASTNode qualifier = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    return qualifier != null ? (PyExpression) qualifier.getPsi() : null;
  }

  public String toString() {
    return super.toString() + ": " + getName();
  }

  public Icon getIcon(final int flags) {
    if (getQualifier() != null || PsiTreeUtil.<PsiElement>getParentOfType(this, PyFunction.class, PyClass.class) instanceof PyClass) {
      return PlatformIcons.FIELD_ICON;
    }
    return PlatformIcons.VARIABLE_ICON;
  }

  @Nullable
  public PyExpression findAssignedValue() {
    if (isValid()) {
      PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(this, PyAssignmentStatement.class);
      if (assignment != null) {
        List<Pair<PyExpression, PyExpression>> mapping = assignment.getTargetsToValuesMapping();
        for (Pair<PyExpression, PyExpression> pair : mapping) {
          PyExpression assigned_to = pair.getFirst();
          if (assigned_to == this) return pair.getSecond();
        }
      }
    }
    return null;
  }

  public PyQualifiedName getAssignedQName() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.ReferenceExpression) {
        return stub.getInitializer();
      }
      return null;
    }
    return PyQualifiedName.fromExpression(findAssignedValue());
  }

  @Override
  public PyQualifiedName getCalleeName() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      final PyTargetExpressionStub.InitializerType initializerType = stub.getInitializerType();
      if (initializerType == PyTargetExpressionStub.InitializerType.CallExpression) {
        return stub.getInitializer();
      }
      else if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
        return stub.getCustomStub(CustomTargetExpressionStub.class).getCalleeName();
      }
      return null;
    }
    final PyExpression value = findAssignedValue();
    if (value instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)value).getCallee();
      return PyQualifiedName.fromExpression(callee);
    }
    return null;
  }

  @NotNull
  @Override
  public PsiReference getReference() {
    if (getQualifier() != null) {
      return new PyQualifiedReference(this, PyResolveContext.defaultContext());
    }
    return new PyTargetReference(this, PyResolveContext.defaultContext());
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(this);
    if (owner != null) {
      final Scope scope = ControlFlowCache.getScope(owner);
      if (scope.isGlobal(getName())) {
        return GlobalSearchScope.projectScope(getProject());
      }
      if (scope.isNonlocal(getName())) {
        return new LocalSearchScope(getContainingFile());
      }
    }

    // find highest level function containing our var
    PyElement container = this;
    while(true) {
      PyElement parentContainer = PsiTreeUtil.getParentOfType(container, PyFunction.class, PyClass.class);
      if (parentContainer instanceof PyClass) {
        if (getQualifier() != null) {
          return super.getUseScope();
        }
        break;
      }
      if (parentContainer == null) {
        break;
      }
      container = parentContainer;
    }
    if (container instanceof PyFunction) {
      return new LocalSearchScope(container);
    }
    return super.getUseScope();
  }

  @Override
  public PyClass getContainingClass() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      final StubElement parentStub = stub.getParentStub();
      if (parentStub instanceof PyClassStub) {
        return ((PyClassStub)parentStub).getPsi();
      }
      if (parentStub instanceof PyFunctionStub) {
        final StubElement functionParent = parentStub.getParentStub();
        if (functionParent instanceof PyClassStub) {
          return ((PyClassStub) functionParent).getPsi();
        }
      }

      return null;
    }

    PsiElement parent = PsiTreeUtil.getParentOfType(this, PyStatementList.class);
    if (parent != null) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof PyClass) {
        return (PyClass)pparent;
      }
      if (pparent instanceof PyFunction) {
        return ((PyFunction) pparent).getContainingClass();
      }
    }
    return null;
  }

  protected String getElementLocation() {
    final PyClass containingClass = getContainingClass();
    if (containingClass != null) {
      return "(" + containingClass.getName() + " in " + getPackageForFile(getContainingFile()) + ")";
    }
    return super.getElementLocation();
  }
}
