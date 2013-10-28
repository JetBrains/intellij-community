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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyBlockEvaluator {
  private final Map<String, Object> myNamespace = new HashMap<String, Object>();
  private final Map<String, List<PyExpression>> myDeclarations = new HashMap<String, List<PyExpression>>();
  private final Set<PyFile> myVisitedFiles;
  private final Set<String> myDeclarationsToTrack = new HashSet<String>();
  private String myCurrentFilePath;
  private Object myReturnValue;
  private boolean myEvaluateCollectionItems = true;

  public PyBlockEvaluator() {
    myVisitedFiles = new HashSet<PyFile>();
  }

  public PyBlockEvaluator(Set<PyFile> visitedFiles) {
    myVisitedFiles = visitedFiles;
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
    PyElement statementContainer = element instanceof PyFunction ? ((PyFunction) element).getStatementList() : element;
    if (statementContainer == null) {
      return;
    }
    statementContainer.acceptChildren(new PyElementVisitor() {
      @Override
      public void visitPyAssignmentStatement(PyAssignmentStatement node) {
        PyExpression expression = node.getLeftHandSideExpression();
        if (expression instanceof PyTargetExpression) {
          String name = expression.getName();
          PyExpression value = ((PyTargetExpression)expression).findAssignedValue();
          myNamespace.put(name, prepareEvaluator().evaluate(value));
          if (myDeclarationsToTrack.contains(name)) {
            List<PyExpression> declarations = new ArrayList<PyExpression>();
            PyPsiUtils.sequenceToList(declarations, value);
            myDeclarations.put(name, declarations);
          }
        }
        else if (expression instanceof PySubscriptionExpression) {
          PyExpression operand = ((PySubscriptionExpression)expression).getOperand();
          PyExpression indexExpression = ((PySubscriptionExpression)expression).getIndexExpression();
          if (operand instanceof PyReferenceExpression && ((PyReferenceExpression)operand).getQualifier() == null) {
            Object currentValue = myNamespace.get(((PyReferenceExpression)operand).getReferencedName());
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
        if (target instanceof PyReferenceExpression && ((PyReferenceExpression)target).getQualifier() == null && name != null) {
          Object currentValue = myNamespace.get(name);
          if (currentValue != null) {
            Object rhs = prepareEvaluator().evaluate(node.getValue());
            myNamespace.put(name, PyEvaluator.concatenate(currentValue, rhs));
          }
          if (myDeclarationsToTrack.contains(name)) {
            List<PyExpression> declarations = myDeclarations.get(name);
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
            if (qualifierRef.getQualifier() == null) {
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
      public void visitPyFromImportStatement(PyFromImportStatement node) {
        if (node.isFromFuture()) return;
        PsiElement source = PyUtil.turnDirIntoInit(node.resolveImportSource());
        if (source instanceof PyFile) {
          PyBlockEvaluator importEvaluator = new PyBlockEvaluator(myVisitedFiles);
          importEvaluator.myDeclarationsToTrack.addAll(myDeclarationsToTrack);
          importEvaluator.evaluate((PyFile)source);
          if (node.isStarImport()) {
            // TODO honor __all__ here
            myNamespace.putAll(importEvaluator.myNamespace);
            myDeclarations.putAll(importEvaluator.myDeclarations);
          }
          else {
            for (PyImportElement element : node.getImportElements()) {
              Object value = importEvaluator.myNamespace.get(element.getName());
              String name = element.getAsName();
              if (name == null) {
                name = element.getName();
              }
              myNamespace.put(name, value);
              List<PyExpression> declarations = importEvaluator.getDeclarations(name);
              if (myDeclarations.containsKey(name)) {
                myDeclarations.get(name).addAll(declarations);
              }
              else {
                myDeclarations.put(name, declarations);
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
    });
  }

  private void processExtendCall(PyCallExpression node, String nameBeingExtended) {
    PyExpression arg = node.getArguments()[0];

    Object value = myNamespace.get(nameBeingExtended);
    if (value instanceof List) {
      Object argValue = prepareEvaluator().evaluate(arg);
      myNamespace.put(nameBeingExtended, PyEvaluator.concatenate(value, argValue));
    }

    if (myDeclarationsToTrack.contains(nameBeingExtended)) {
      List<PyExpression> declarations = myDeclarations.get(nameBeingExtended);
      if (declarations != null) {
        PyPsiUtils.sequenceToList(declarations, arg);
      }
    }
  }

  private void processUpdateCall(PyCallExpression node, String name) {
    Object value = myNamespace.get(name);
    if (value instanceof Map) {
      Object argValue = prepareEvaluator().evaluate(node.getArguments()[0]);
      if (argValue instanceof Map) {
        ((Map)value).putAll((Map)argValue);
      }
    }
  }

  private PyEvaluator prepareEvaluator() {
    PyEvaluator evaluator = createEvaluator();
    evaluator.setNamespace(myNamespace);
    evaluator.setEvaluateCollectionItems(myEvaluateCollectionItems);
    return evaluator;
  }

  protected PyEvaluator createEvaluator() {
    return new PyPathEvaluator(myCurrentFilePath);
  }

  public Object getValue(String name) {
    return myNamespace.get(name);
  }

  @Nullable
  public String getValueAsString(String name) {
    Object value = myNamespace.get(name);
    return value instanceof String ? (String) value : null;
  }

  @Nullable
  public List getValueAsList(String name) {
    Object value = myNamespace.get(name);
    return value instanceof List ? (List) value : null;
  }

  @NotNull
  public List<String> getValueAsStringList(String name) {
    Object value = myNamespace.get(name);
    if (value instanceof List) {
      List valueList = (List) value;
      for (Object o : valueList) {
        if (o != null && !(o instanceof String)) {
          return Collections.emptyList();
        }
      }
      return (List<String>) value;
    }
    if (value instanceof String) {
      return Collections.singletonList((String) value);
    }
    return Collections.emptyList();
  }

  public Set<PyFile> getVisitedFiles() {
    return myVisitedFiles;
  }

  @NotNull
  public List<PyExpression> getDeclarations(String name) {
    List<PyExpression> expressions = myDeclarations.get(name);
    return expressions != null ? expressions : Collections.<PyExpression>emptyList();
  }

  public Object getReturnValue() {
    return myReturnValue;
  }

  public void setEvaluateCollectionItems(boolean evaluateCollectionItems) {
    myEvaluateCollectionItems = evaluateCollectionItems;
  }
}
