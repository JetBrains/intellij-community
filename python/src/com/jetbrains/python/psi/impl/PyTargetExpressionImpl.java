/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
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
import com.jetbrains.python.documentation.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.psi.impl.references.PyTargetReference;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyTargetExpressionImpl extends PyBaseElementImpl<PyTargetExpressionStub> implements PyTargetExpression {
  QualifiedName myQualifiedName;

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
      final ASTNode nameElement = PyUtil.createNewName(this, name);
      getNode().replaceChild(oldNameElement, nameElement);
    }
    return this;
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return getTypeWithAnchor(context, null);
  }

  @Nullable
  public PyType getTypeWithAnchor(TypeEvalContext context, @Nullable PsiElement anchor) {
    if (!TypeEvalStack.mayEvaluate(this)) {
      return null;
    }
    try {
      if (PyNames.ALL.equals(getName())) {
        // no type for __all__, to avoid unresolved reference errors for expressions where a qualifier is a name
        // imported via __all__
        return null;
      }
      final PyType pyType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(this, context, anchor);
      if (pyType != null) {
        return pyType;
      }
      PyType type = getTypeFromDocString();
      if (type != null) {
        return type;
      }
      if (!context.maySwitchToAST(this)) {
        final PsiElement value = getStub() != null ? findAssignedValueByStub(context) : findAssignedValue();
        if (value instanceof PyTypedElement) {
          type = context.getType((PyTypedElement)value);
          if (type instanceof PyNoneType) {
            return null;
          }
          if (type instanceof PyFunctionTypeImpl) {
            return type;
          }
          // We are unsure about the type since it may be inferred from the stub based on incomplete information
          return PyUnionType.createWeakType(type);
        }
        return null;
      }
      type = getTypeFromComment(this);
      if (type != null) {
        return type;
      }
      final PsiElement parent = getParent();
      if (parent instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)parent;
        PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue instanceof PyParenthesizedExpression) {
          assignedValue = ((PyParenthesizedExpression)assignedValue).getContainedExpression();
        }
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
              final PyType cfgType = refex.getQualifiedReferenceTypeByControlFlow(context);
              if (cfgType != null) {
                return cfgType;
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
        PsiElement nextParent = parent.getParent();
        while (nextParent instanceof PyParenthesizedExpression) {
          nextParent = nextParent.getParent();
        }
        if (nextParent instanceof PyAssignmentStatement) {
          final PyAssignmentStatement assignment = (PyAssignmentStatement)nextParent;
          final PyExpression value = assignment.getAssignedValue();
          if (value != null) {
            final PyType assignedType = PyTypeChecker.toNonWeakType(context.getType(value), context);
            if (assignedType instanceof PyTupleType) {
              final PyType t = PyTypeChecker.getTargetTypeFromTupleAssignment(this, (PyTupleExpression)parent, (PyTupleType)assignedType);
              if (t != null) {
                return t;
              }
            }
          }
        }
      }
      if (parent instanceof PyWithItem) {
        return getWithItemVariableType(context, (PyWithItem)parent);
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
  private static PyType getWithItemVariableType(TypeEvalContext context, PyWithItem item) {
    final PyExpression expression = item.getExpression();
    if (expression != null) {
      final PyType exprType = context.getType(expression);
      if (exprType instanceof PyClassType) {
        final PyClass cls = ((PyClassType)exprType).getPyClass();
        final PyFunction enter = cls.findMethodByName(PyNames.ENTER, true);
        if (enter != null) {
          final PyType enterType = enter.getCallType(expression, Collections.<PyExpression, PyNamedParameter>emptyMap(), context);
          if (enterType != null) {
            return enterType;
          }
          for (PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
            PyType typeFromProvider = provider.getContextManagerVariableType(cls, expression, context);
            if (typeFromProvider != null) {
              return typeFromProvider;
            }
          }
          // Guess the return type of __enter__
          return PyUnionType.createWeakType(exprType);
        }
      }
    }
    return null;
  }

  @Nullable
  public PyType getTypeFromDocString() {
    String typeName = null;
    final String name = getName();
    final StructuredDocString targetDocString = getStructuredDocString();
    if (targetDocString != null) {
      typeName = targetDocString.getParamType(null);
      if (typeName == null) {
        typeName = targetDocString.getParamType(name);
      }
    }
    if (typeName == null && PyUtil.isAttribute(this)) {
      final PyClass cls = getContainingClass();
      if (cls != null) {
        final StructuredDocString classDocString = cls.getStructuredDocString();
        if (classDocString != null) {
          typeName = classDocString.getParamType(name);
        }
      }
    }
    if (typeName != null) {
      return PyTypeParser.getTypeByName(this, typeName);
    }
    return null;
  }

  @Nullable
  public static PyType getTypeFromComment(PyTargetExpressionImpl targetExpression) {
    String docComment = DocStringUtil.getAttributeDocComment(targetExpression);
    if (docComment != null) {
      StructuredDocString structuredDocString = DocStringUtil.parse(docComment);
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
    if (source != null) {
      final PyType sourceType = context.getType(source);
      final PyType type = getIterationType(sourceType, source, this, context);
      if (type instanceof PyTupleType && target instanceof PyTupleExpression) {
        return PyTypeChecker.getTargetTypeFromTupleAssignment(this, (PyTupleExpression)target, (PyTupleType)type);
      }
      if (target == this && type != null) {
        return type;
      }
    }
    return null;
  }

  @Nullable
  private static PyType getIterationType(@Nullable PyType iterableType, @Nullable PyExpression source, @NotNull PsiElement anchor,
                                         @NotNull TypeEvalContext context) {
    PyType result = null;
    if (iterableType instanceof PyCollectionType) {
      result = ((PyCollectionType)iterableType).getElementType(context);
      if (iterableType instanceof PyClassType) {
        final PyClass cls = ((PyClassType)iterableType).getPyClass();
        if (result instanceof PyTupleType && PyABCUtil.isSubclass(cls, PyNames.MAPPING)) {
          final PyTupleType mappingType = (PyTupleType)result;
          if (mappingType.getElementCount() == 2) {
            result = mappingType.getElementType(0);
          }
        }
      }
    }
    else if (iterableType instanceof PyTupleType) {
      final PyTupleType tupleType = (PyTupleType)iterableType;
      final List<PyType> memberTypes = new ArrayList<PyType>();
      for (int i = 0; i < tupleType.getElementCount(); i++) {
        memberTypes.add(tupleType.getElementType(i));
      }
      return PyUnionType.union(memberTypes);
    }
    else if (iterableType instanceof PyUnionType) {
      final Collection<PyType> members = ((PyUnionType)iterableType).getMembers();
      final List<PyType> iterationTypes = new ArrayList<PyType>();
      for (PyType member : members) {
        iterationTypes.add(getIterationType(member, source, anchor, context));
      }
      return PyUnionType.union(iterationTypes);
    }
    else if (iterableType != null && PyABCUtil.isSubtype(iterableType, PyNames.ITERATOR, context)) {
      final PyFunction iterateMethod = findMethodByName(iterableType, PyNames.ITER, context);
      PyType iterateMethodType = null;
      if (iterateMethod != null) {
        iterateMethodType = getContextSensitiveType(iterateMethod, context, source);
      }
      if (iterateMethodType instanceof PyCollectionType) {
        final PyCollectionType collectionType = (PyCollectionType)iterateMethodType;
        result = collectionType.getElementType(context);
      }
      if (result == null) {
        final String nextMethodName = LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON30) ?
                                      PyNames.DUNDER_NEXT : PyNames.NEXT;
        final PyFunction next = findMethodByName(iterableType, nextMethodName, context);
        if (next != null) {
          result = getContextSensitiveType(next, context, source);
        }
      }
      if (result == null) {
        final PyFunction getItem = findMethodByName(iterableType, PyNames.GETITEM, context);
        if (getItem != null) {
          result = getContextSensitiveType(getItem, context, source);
        }
      }
    }
    return result;
  }

  @Nullable
  private static PyFunction findMethodByName(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
    final List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
    if (results != null && !results.isEmpty()) {
      final RatedResolveResult result = results.get(0);
      final PsiElement element = result.getElement();
      if (element instanceof PyFunction) {
        return (PyFunction)element;
      }
    }
    return null;
  }

  @Nullable
  private static PyType getContextSensitiveType(@NotNull PyFunction function, @NotNull TypeEvalContext context,
                                                @Nullable PyExpression source) {
    return function.getCallType(source, Collections.<PyExpression, PyNamedParameter>emptyMap(), context);
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
        return new PyClassTypeImpl((PyClass) element, false);
      }
    }
    return null;
  }

  public PyExpression getQualifier() {
    ASTNode qualifier = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
    return qualifier != null ? (PyExpression) qualifier.getPsi() : null;
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    if (myQualifiedName == null) {
      myQualifiedName = PyPsiUtils.asQualifiedName(this);
    }
    return myQualifiedName;
  }

  public String toString() {
    return super.toString() + ": " + getName();
  }

  public Icon getIcon(final int flags) {
    if (isQualified() || PsiTreeUtil.getStubOrPsiParentOfType(this, PyDocStringOwner.class) instanceof PyClass) {
      return PlatformIcons.FIELD_ICON;
    }
    return PlatformIcons.VARIABLE_ICON;
  }

  public boolean isQualified() {
    PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.isQualified();
    }
    return getQualifier() != null;
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

  public QualifiedName getAssignedQName() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.ReferenceExpression) {
        return stub.getInitializer();
      }
      return null;
    }
    return PyPsiUtils.asQualifiedName(findAssignedValue());
  }

  @Nullable
  public PsiElement findAssignedValueByStub(@NotNull TypeEvalContext context) {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null && stub.getInitializerType() == PyTargetExpressionStub.InitializerType.ReferenceExpression) {
      final QualifiedName initializer = stub.getInitializer();
      // TODO: Support qualified stub initializers
      if (initializer != null && initializer.getComponentCount() == 1) {
        final String name = initializer.getLastComponent();
        if (name != null) {
          final PsiElement parent = getParentByStub();
          if (parent instanceof PyFile) {
            return ((PyFile)parent).getElementNamed(name);
          }
          else if (parent instanceof PyClass) {
            final PyType type = context.getType((PyClass)parent);
            if (type != null) {
              final List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ,
                                                                                    PyResolveContext.noImplicits());
              if (results != null && !results.isEmpty()) {
                return results.get(0).getElement();
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public QualifiedName getCalleeName() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      final PyTargetExpressionStub.InitializerType initializerType = stub.getInitializerType();
      if (initializerType == PyTargetExpressionStub.InitializerType.CallExpression) {
        return stub.getInitializer();
      }
      else if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
        final CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
        if (customStub != null) {
          return customStub.getCalleeName();
        }
      }
      return null;
    }
    final PyExpression value = findAssignedValue();
    if (value instanceof PyCallExpression) {
      final PyExpression callee = ((PyCallExpression)value).getCallee();
      return PyPsiUtils.asQualifiedName(callee);
    }
    return null;
  }

  @NotNull
  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.defaultContext());
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference(final PyResolveContext resolveContext) {
    if (isQualified()) {
      return new PyQualifiedReference(this, resolveContext);
    }
    return new PyTargetReference(this, resolveContext);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    if (isQualified()) {
      return super.getUseScope();
    }
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
        if (isQualified()) {
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

    final PsiElement parent = PsiTreeUtil.getParentOfType(this, PyFunction.class, PyClass.class);
    if (parent instanceof PyClass) {
      return (PyClass)parent;
    }
    if (parent instanceof PyFunction) {
      return ((PyFunction)parent).getContainingClass();
    }
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new PyElementPresentation(this) {
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
  @Override
  public String getDocStringValue() {
    final PyTargetExpressionStub stub = getStub();
    if (stub != null) {
      return stub.getDocString();
    }
    return DocStringUtil.getDocStringValue(this);
  }

  @Nullable
  @Override
  public StructuredDocString getStructuredDocString() {
    return DocStringUtil.getStructuredDocString(this);
  }

  @Nullable
  @Override
  public PyStringLiteralExpression getDocStringExpression() {
    final PsiElement parent = getParent();
    if (parent instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)parent;
      PsiElement nextSibling = assignment.getNextSibling();
      while (nextSibling != null && (nextSibling instanceof PsiWhiteSpace || nextSibling instanceof PsiComment)) {
        nextSibling = nextSibling.getNextSibling();
      }
      if (nextSibling instanceof PyExpressionStatement) {
        final PyExpression expression = ((PyExpressionStatement)nextSibling).getExpression();
        if (expression instanceof PyStringLiteralExpression) {
          return (PyStringLiteralExpression)expression;
        }
      }
    }
    return null;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myQualifiedName = null;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return QualifiedNameFinder.getQualifiedName(this);
  }
}
