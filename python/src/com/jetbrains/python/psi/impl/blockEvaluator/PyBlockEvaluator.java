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
package com.jetbrains.python.psi.impl.blockEvaluator;

import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.impl.PyPathEvaluator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyBlockEvaluator {
  @NotNull
  private final PyEvaluationResult myEvaluationResult = new PyEvaluationResult();
  @NotNull
  private final PyEvaluationContext myContext;
  private final Set<PyFile> myVisitedFiles;
  private final Set<String> myDeclarationsToTrack = new HashSet<>();
  private String myCurrentFilePath;
  private Object myReturnValue;
  private boolean myEvaluateCollectionItems = true;

  /**
   * @param evaluationContext context, obtained via {@link #getContext()}. Pass it here to enable cache. See {@link com.jetbrains.python.psi.impl.blockEvaluator.PyEvaluationContext}
   *                          for more info
   * @see com.jetbrains.python.psi.impl.blockEvaluator.PyEvaluationContext
   */
  public PyBlockEvaluator(@NotNull final PyEvaluationContext evaluationContext) {
    this(Sets.<PyFile>newHashSet(), evaluationContext);
  }

  /**
   * Create evaluator with out of cache context
   */
  public PyBlockEvaluator() {
    this(new PyEvaluationContext());
  }

  private PyBlockEvaluator(@NotNull final Set<PyFile> visitedFiles, @NotNull final PyEvaluationContext evaluationContext) {
    myVisitedFiles = visitedFiles;
    myContext = evaluationContext;
  }

  public void trackDeclarations(String attrName) {
    myDeclarationsToTrack.add(attrName);
  }

  public void evaluate(PyElement element) {
    VirtualFile vFile = element.getContainingFile().getVirtualFile();
    myCurrentFilePath = vFile != null ? vFile.getPath() : null;
    if (myVisitedFiles.contains(element)) {
      return;
    }
    myVisitedFiles.add((PyFile)element.getContainingFile());
    PyElement statementContainer = element instanceof PyFunction ? ((PyFunction)element).getStatementList() : element;
    if (statementContainer == null) {
      return;
    }
    statementContainer.acceptChildren(new MyPyElementVisitor());
  }

  private void processExtendCall(PyCallExpression node, String nameBeingExtended) {
    PyExpression arg = node.getArguments()[0];

    Object value = myEvaluationResult.myNamespace.get(nameBeingExtended);
    if (value instanceof List) {
      Object argValue = prepareEvaluator().evaluate(arg);
      myEvaluationResult.myNamespace.put(nameBeingExtended, prepareEvaluator().concatenate(value, argValue));
    }

    if (myDeclarationsToTrack.contains(nameBeingExtended)) {
      List<PyExpression> declarations = myEvaluationResult.myDeclarations.get(nameBeingExtended);
      if (declarations != null) {
        PyPsiUtils.sequenceToList(declarations, arg);
      }
    }
  }

  private void processUpdateCall(PyCallExpression node, String name) {
    Object value = myEvaluationResult.myNamespace.get(name);
    if (value instanceof Map) {
      Object argValue = prepareEvaluator().evaluate(node.getArguments()[0]);
      if (argValue instanceof Map) {
        ((Map)value).putAll((Map)argValue);
      }
    }
  }

  private PyEvaluator prepareEvaluator() {
    PyEvaluator evaluator = createEvaluator();
    evaluator.setNamespace(myEvaluationResult.myNamespace);
    evaluator.setEvaluateCollectionItems(myEvaluateCollectionItems);
    return evaluator;
  }

  protected PyEvaluator createEvaluator() {
    return new PyPathEvaluator(myCurrentFilePath);
  }

  public Object getValue(String name) {
    return myEvaluationResult.myNamespace.get(name);
  }

  @Nullable
  public String getValueAsString(String name) {
    Object value = myEvaluationResult.myNamespace.get(name);
    return value instanceof String ? (String)value : null;
  }

  @Nullable
  public List getValueAsList(String name) {
    Object value = myEvaluationResult.myNamespace.get(name);
    return value instanceof List ? (List)value : null;
  }

  @NotNull
  public List<String> getValueAsStringList(String name) {
    Object value = myEvaluationResult.myNamespace.get(name);
    if (value instanceof List) {
      List valueList = (List)value;
      for (Object o : valueList) {
        if (o != null && !(o instanceof String)) {
          return Collections.emptyList();
        }
      }
      return (List<String>)value;
    }
    if (value instanceof String) {
      return Collections.singletonList((String)value);
    }
    return Collections.emptyList();
  }

  public Set<PyFile> getVisitedFiles() {
    return myVisitedFiles;
  }


  public Object getReturnValue() {
    return myReturnValue;
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }

  @NotNull
  public List<PyExpression> getDeclarations(@NotNull final String name) {
    return myEvaluationResult.getDeclarations(name);
  }

  /**
   * @return so-called context. You may pass it to any instance of {@link com.jetbrains.python.psi.impl.blockEvaluator.PyBlockEvaluator}
   * to make instances share their cache
   */
  @NotNull
  public PyEvaluationContext getContext() {
    return myContext;
  }

  private class MyPyElementVisitor extends PyElementVisitor {
    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      PyExpression expression = node.getLeftHandSideExpression();
      if (expression instanceof PyTargetExpression) {
        String name = expression.getName();
        PyExpression value = ((PyTargetExpression)expression).findAssignedValue();
        myEvaluationResult.myNamespace.put(name, prepareEvaluator().evaluate(value));
        if (myDeclarationsToTrack.contains(name)) {
          List<PyExpression> declarations = new ArrayList<>();
          PyPsiUtils.sequenceToList(declarations, value);
          myEvaluationResult.myDeclarations.put(name, declarations);
        }
      }
      else if (expression instanceof PySubscriptionExpression) {
        PyExpression operand = ((PySubscriptionExpression)expression).getOperand();
        PyExpression indexExpression = ((PySubscriptionExpression)expression).getIndexExpression();
        if (operand instanceof PyReferenceExpression && ((PyReferenceExpression)operand).getQualifier() == null) {
          Object currentValue = myEvaluationResult.myNamespace.get(((PyReferenceExpression)operand).getReferencedName());
          if (currentValue instanceof Map) {
            Object mapKey = prepareEvaluator().evaluate(indexExpression);
            if (mapKey != null) {
              Object value = myEvaluateCollectionItems ? prepareEvaluator().evaluate(node.getAssignedValue()) : node.getAssignedValue();
              ((Map)currentValue).put(mapKey, value);
            }
          }
        }
      }
    }

    @Override
    public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
      PyExpression target = node.getTarget();
      String name = target.getName();
      if (target instanceof PyReferenceExpression && !((PyReferenceExpression)target).isQualified() && name != null) {
        Object currentValue = myEvaluationResult.myNamespace.get(name);
        if (currentValue != null) {
          Object rhs = prepareEvaluator().evaluate(node.getValue());
          myEvaluationResult.myNamespace.put(name, prepareEvaluator().concatenate(currentValue, rhs));
        }
        if (myDeclarationsToTrack.contains(name)) {
          List<PyExpression> declarations = myEvaluationResult.myDeclarations.get(name);
          if (declarations != null) {
            PyPsiUtils.sequenceToList(declarations, node.getValue());
          }
        }
      }
    }

    @Override
    public void visitPyExpressionStatement(PyExpressionStatement node) {
      node.getExpression().accept(this);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      PyExpression callee = node.getCallee();
      if (callee instanceof PyReferenceExpression) {
        PyReferenceExpression calleeRef = (PyReferenceExpression)callee;
        PyExpression qualifier = calleeRef.getQualifier();
        if (qualifier instanceof PyReferenceExpression) {
          PyReferenceExpression qualifierRef = (PyReferenceExpression)qualifier;
          if (!qualifierRef.isQualified()) {
            if (PyNames.EXTEND.equals(calleeRef.getReferencedName()) && node.getArguments().length == 1) {
              processExtendCall(node, qualifierRef.getReferencedName());
            }
            else if (PyNames.UPDATE.equals(calleeRef.getReferencedName()) && node.getArguments().length == 1) {
              processUpdateCall(node, qualifierRef.getReferencedName());
            }
          }
        }
      }
    }

    @Override
    public void visitPyFromImportStatement(final PyFromImportStatement node) {
      if (node.isFromFuture()) return;
      final PsiElement source = PyUtil.turnDirIntoInit(node.resolveImportSource());
      if (source instanceof PyFile) {
        final PyFile pyFile = (PyFile)source;
        PyEvaluationResult newlyEvaluatedResult = myContext.getCachedResult(pyFile);

        if (newlyEvaluatedResult == null) {
          final PyBlockEvaluator importEvaluator = new PyBlockEvaluator(myVisitedFiles, myContext);
          importEvaluator.myDeclarationsToTrack.addAll(myDeclarationsToTrack);
          importEvaluator.evaluate(pyFile);
          newlyEvaluatedResult = importEvaluator.myEvaluationResult;
          myContext.cache(pyFile, newlyEvaluatedResult);
        }

        if (node.isStarImport()) {
          // TODO honor __all__ here
          myEvaluationResult.myNamespace.putAll(newlyEvaluatedResult.myNamespace);
          myEvaluationResult.myDeclarations.putAll(newlyEvaluatedResult.myDeclarations);
        }
        else {
          for (final PyImportElement element : node.getImportElements()) {
            final String nameOfVarInOurModule = element.getVisibleName();
            final QualifiedName nameOfVarInExternalModule = element.getImportedQName();
            if ((nameOfVarInOurModule == null) || (nameOfVarInExternalModule == null)) {
              continue;
            }

            final Object value = newlyEvaluatedResult.myNamespace.get(nameOfVarInExternalModule.toString());
            myEvaluationResult.myNamespace.put(nameOfVarInOurModule, value);
            final List<PyExpression> declarations = newlyEvaluatedResult.getDeclarations(nameOfVarInOurModule);
            if (myEvaluationResult.myDeclarations.containsKey(nameOfVarInOurModule)) {
              myEvaluationResult.myDeclarations.get(nameOfVarInOurModule).addAll(declarations);
            }
            else {
              myEvaluationResult.myDeclarations.put(nameOfVarInOurModule, declarations);
            }
          }
        }
      }
    }

    @Override
    public void visitPyIfStatement(PyIfStatement node) {
      PyStatementList list = node.getIfPart().getStatementList();
      if (list != null) {
        list.acceptChildren(this);
      }
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      myReturnValue = prepareEvaluator().evaluate(node.getExpression());
    }
  }
}
