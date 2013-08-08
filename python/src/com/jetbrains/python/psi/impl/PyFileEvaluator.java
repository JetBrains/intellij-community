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
  private final Map<String, List<PyExpression>> myDeclarations = new HashMap<String, List<PyExpression>>();
  private final Set<PyFile> myVisitedFiles;
  private final Set<String> myDeclarationsToTrack = new HashSet<String>();
  private String myCurrentFilePath;

  public PyFileEvaluator() {
    myVisitedFiles = new HashSet<PyFile>();
  }

  public PyFileEvaluator(Set<PyFile> visitedFiles) {
    myVisitedFiles = visitedFiles;
  }

  public void trackDeclarations(String attrName) {
    myDeclarationsToTrack.add(attrName);
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
          if (myDeclarationsToTrack.contains(name)) {
            List<PyExpression> declarations = new ArrayList<PyExpression>();
            PyPsiUtils.sequenceToList(declarations, value);
            myDeclarations.put(name, declarations);
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
            Object rhs = createEvaluator().evaluate(node.getValue());
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
          if (PyNames.EXTEND.equals(calleeRef.getReferencedName()) && node.getArguments().length == 1) {
            PyExpression qualifier = calleeRef.getQualifier();
            if (qualifier instanceof PyReferenceExpression) {
              PyReferenceExpression qualifierRef = (PyReferenceExpression)qualifier;
              if (qualifierRef.getQualifier() == null) {
                processExtendCall(node, qualifierRef.getReferencedName());
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

  private void processExtendCall(PyCallExpression node, String nameBeingExtended) {
    PyExpression arg = node.getArguments()[0];

    Object value = myNamespace.get(nameBeingExtended);
    if (value instanceof List) {
      Object argValue = createEvaluator().evaluate(arg);
      myNamespace.put(nameBeingExtended, PyEvaluator.concatenate(value, argValue));
    }

    if (myDeclarationsToTrack.contains(nameBeingExtended)) {
      List<PyExpression> declarations = myDeclarations.get(nameBeingExtended);
      if (declarations != null) {
        PyPsiUtils.sequenceToList(declarations, arg);
      }
    }
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

  @NotNull
  public List<PyExpression> getDeclarations(String name) {
    List<PyExpression> expressions = myDeclarations.get(name);
    return expressions != null ? expressions : Collections.<PyExpression>emptyList();
  }
}
