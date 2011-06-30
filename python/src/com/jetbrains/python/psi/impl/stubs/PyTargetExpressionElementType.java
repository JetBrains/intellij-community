package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;

import java.io.IOException;

/**
 * @author yole
 */
public class PyTargetExpressionElementType extends PyStubElementType<PyTargetExpressionStub, PyTargetExpression> {
  private CustomTargetExpressionStubType[] myCustomStubTypes;

  public PyTargetExpressionElementType() {
    super("TARGET_EXPRESSION");
  }

  private CustomTargetExpressionStubType[] getCustomStubTypes() {
    if (myCustomStubTypes == null) {
      myCustomStubTypes = Extensions.getExtensions(CustomTargetExpressionStubType.EP_NAME);
    }
    return myCustomStubTypes;
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyTargetExpressionImpl(node);
  }

  public PyTargetExpression createPsi(final PyTargetExpressionStub stub) {
    return new PyTargetExpressionImpl(stub);
  }

  public PyTargetExpressionStub createStub(final PyTargetExpression psi, final StubElement parentStub) {
    final String name = psi.getName();
    final PyExpression assignedValue = psi.findAssignedValue();
    for (CustomTargetExpressionStubType customStubType : getCustomStubTypes()) {
      CustomTargetExpressionStub customStub = customStubType.createStub(psi);
      if (customStub != null) {
        return new PyTargetExpressionStubImpl(name, customStub, parentStub);
      }
    }
    PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.Other;
    PyQualifiedName initializer = null;
    if (assignedValue instanceof PyReferenceExpression) {
      initializerType = PyTargetExpressionStub.InitializerType.ReferenceExpression;
      initializer = ((PyReferenceExpression) assignedValue).asQualifiedName();
    }
    else if (assignedValue instanceof PyCallExpression) {
      initializerType = PyTargetExpressionStub.InitializerType.CallExpression;
      final PyExpression callee = ((PyCallExpression)assignedValue).getCallee();
      if (callee instanceof PyReferenceExpression) {
        initializer = ((PyReferenceExpression) callee).asQualifiedName();
      }
    }
    return new PyTargetExpressionStubImpl(name, initializerType, initializer, parentStub);
  }

  public void serialize(final PyTargetExpressionStub stub, final StubOutputStream stream)
      throws IOException {
    stream.writeName(stub.getName());
    stream.writeVarInt(stub.getInitializerType().getIndex());
    final CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
    if (customStub != null) {
      stream.writeName(customStub.getTypeClass().getCanonicalName());
      customStub.serialize(stream);
    }
    else {
      PyQualifiedName.serialize(stub.getInitializer(), stream);
    }
  }

  public PyTargetExpressionStub deserialize(final StubInputStream stream, final StubElement parentStub)
      throws IOException {
    String name = StringRef.toString(stream.readName());
    PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.fromIndex(stream.readVarInt());
    if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
      final String typeName = stream.readName().getString();
      for(CustomTargetExpressionStubType type: getCustomStubTypes()) {
        if (type.getClass().getCanonicalName().equals(typeName)) {
          CustomTargetExpressionStub stub = type.deserializeStub(stream);
          return new PyTargetExpressionStubImpl(name, stub, parentStub);
        }
      }
      throw new IOException("Unknown custom stub type " + typeName);
    }
    PyQualifiedName initializer = PyQualifiedName.deserialize(stream);
    return new PyTargetExpressionStubImpl(name, initializerType, initializer, parentStub);
  }

  public boolean shouldCreateStub(final ASTNode node) {
    if (PsiTreeUtil.getParentOfType(node.getPsi(), PyComprehensionElement.class, true, PyDocStringOwner.class) != null) {
      return false;
    }
    final ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
    final ASTNode qualifierNode = node.findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
    if (functionNode != null && qualifierNode != null) {
      final ASTNode parameterList = functionNode.findChildByType(PyElementTypes.PARAMETER_LIST);
      assert parameterList != null;
      final ASTNode[] children = parameterList.getChildren(PyElementTypes.FORMAL_PARAMETER_SET);
      if (children.length > 0 && children[0].getText().equals(qualifierNode.getText())) {
        return true;
      }
    }
    return functionNode == null && qualifierNode == null;
  }

  @Override
  public void indexStub(PyTargetExpressionStub stub, IndexSink sink) {
    if (stub.getParentStub() instanceof PyFileStub) {
      String name = stub.getName();
      if (name != null && PyUtil.getInitialUnderscores(name) == 0) {
        sink.occurrence(PyVariableNameIndex.KEY, name);
      }
    }
    for (CustomTargetExpressionStubType stubType : getCustomStubTypes()) {
      stubType.indexStub(stub, sink);
    }
  }
}
