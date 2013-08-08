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
public class PyFileEvaluator {
  private final Map<String, Object> myNamespace = new HashMap<String, Object>();
  private final Set<PyFile> myVisitedFiles;
  private String myCurrentFilePath;

  public PyFileEvaluator() {
    myVisitedFiles = new HashSet<PyFile>();
  }

  public PyFileEvaluator(Set<PyFile> visitedFiles) {
    myVisitedFiles = visitedFiles;
  }

  public void evaluate(PyFile file) {
    VirtualFile vFile = file.getVirtualFile();
    myCurrentFilePath = vFile != null ? vFile.getPath() : null;
    if (myVisitedFiles.contains(file)) {
      return;
    }
    myVisitedFiles.add(file);
    file.acceptChildren(new PyElementVisitor() {
      @Override
      public void visitPyAssignmentStatement(PyAssignmentStatement node) {
        PyExpression expression = node.getLeftHandSideExpression();
        if (expression instanceof PyTargetExpression) {
          String name = expression.getName();
          PyExpression value = ((PyTargetExpression)expression).findAssignedValue();
          myNamespace.put(name, createEvaluator().evaluate(value));
        }
      }

      @Override
      public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
        PyExpression target = node.getTarget();
        if (target instanceof PyReferenceExpression && ((PyReferenceExpression)target).getQualifier() == null && target.getName() != null) {
          Object currentValue = myNamespace.get(target.getName());
          if (currentValue != null) {
            Object rhs = createEvaluator().evaluate(node.getValue());
            myNamespace.put(target.getName(), PyEvaluator.concatenate(currentValue, rhs));
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
          if (PyNames.EXTEND.equals(calleeRef.getReferencedName()) && node.getArguments().length == 1) {
            PyExpression qualifier = calleeRef.getQualifier();
            if (qualifier instanceof PyReferenceExpression) {
              PyReferenceExpression qualifierRef = (PyReferenceExpression)qualifier;
              if (qualifierRef.getQualifier() == null) {
                String nameBeingExtended = qualifierRef.getReferencedName();
                Object value = myNamespace.get(nameBeingExtended);
                if (value instanceof List) {
                  Object arg = createEvaluator().evaluate(node.getArguments()[0]);
                  myNamespace.put(nameBeingExtended, PyEvaluator.concatenate(value, arg));
                }
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
          PyFileEvaluator importEvaluator = new PyFileEvaluator(myVisitedFiles);
          importEvaluator.evaluate((PyFile)source);
          if (node.isStarImport()) {
            // TODO honor __all__ here
            myNamespace.putAll(importEvaluator.myNamespace);
          }
          else {
            for (PyImportElement element : node.getImportElements()) {
              Object value = importEvaluator.myNamespace.get(element.getName());
              String name = element.getAsName();
              if (name == null) {
                name = element.getName();
              }
              myNamespace.put(name, value);
            }
          }
        }
      }
    });
  }

  private PyEvaluator createEvaluator() {
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
}
