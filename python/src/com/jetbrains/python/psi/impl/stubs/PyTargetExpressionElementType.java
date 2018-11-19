// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class PyTargetExpressionElementType extends PyStubElementType<PyTargetExpressionStub, PyTargetExpression> {
  private List<CustomTargetExpressionStubType> myCustomStubTypes;

  public PyTargetExpressionElementType() {
    super("TARGET_EXPRESSION");
  }

  public PyTargetExpressionElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  private List<CustomTargetExpressionStubType> getCustomStubTypes() {
    if (myCustomStubTypes == null) {
      myCustomStubTypes = CustomTargetExpressionStubType.EP_NAME.getExtensionList();
    }
    return myCustomStubTypes;
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyTargetExpressionImpl(node);
  }

  @Override
  public PyTargetExpression createPsi(@NotNull final PyTargetExpressionStub stub) {
    return new PyTargetExpressionImpl(stub);
  }

  @Override
  @NotNull
  public PyTargetExpressionStub createStub(@NotNull final PyTargetExpression psi, final StubElement parentStub) {
    final String name = psi.getName();
    final PyExpression assignedValue = psi.findAssignedValue();
    final String docString = DocStringUtil.getDocStringValue(psi);
    final String typeComment = psi.getTypeCommentAnnotation();
    final String annotation = psi.getAnnotationValue();

    for (CustomTargetExpressionStubType customStubType : getCustomStubTypes()) {
      CustomTargetExpressionStub customStub = customStubType.createStub(psi);
      if (customStub != null) {
        return new PyTargetExpressionStubImpl(name, docString, typeComment, annotation, psi.hasAssignedValue(), customStub, parentStub);
      }
    }
    PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.Other;
    QualifiedName initializer = null;
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
    return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, psi.isQualified(), typeComment, annotation,
                                          psi.hasAssignedValue(), parentStub);
  }

  @Override
  public void serialize(@NotNull final PyTargetExpressionStub stub, @NotNull final StubOutputStream stream) throws IOException {
    stream.writeName(stub.getName());
    final String docString = stub.getDocString();
    stream.writeUTFFast(docString != null ? docString : "");
    stream.writeVarInt(stub.getInitializerType().getIndex());
    stream.writeName(stub.getTypeComment());
    stream.writeName(stub.getAnnotation());
    stream.writeBoolean(stub.hasAssignedValue());
    final CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
    if (customStub != null) {
      stream.writeName(customStub.getTypeClass().getCanonicalName());
      customStub.serialize(stream);
    }
    else {
      QualifiedName.serialize(stub.getInitializer(), stream);
      stream.writeBoolean(stub.isQualified());
    }
  }

  @Override
  @NotNull
  public PyTargetExpressionStub deserialize(@NotNull final StubInputStream stream, final StubElement parentStub) throws IOException {
    String name = stream.readNameString();
    String docString = stream.readUTFFast();
    if (docString.isEmpty()) {
      docString = null;
    }
    PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.fromIndex(stream.readVarInt());
    String typeComment = stream.readNameString();
    String annotation = stream.readNameString();
    final boolean hasAssignedValue = stream.readBoolean();
    if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
      final String typeName = stream.readNameString();
      for(CustomTargetExpressionStubType type: getCustomStubTypes()) {
        if (type.getClass().getCanonicalName().equals(typeName)) {
          CustomTargetExpressionStub stub = type.deserializeStub(stream);
          return new PyTargetExpressionStubImpl(name, docString, typeComment, annotation, hasAssignedValue, stub, parentStub);
        }
      }
      throw new IOException("Unknown custom stub type " + typeName);
    }
    QualifiedName initializer = QualifiedName.deserialize(stream);
    boolean isQualified = stream.readBoolean();
    return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, isQualified, typeComment, annotation,
                                          hasAssignedValue, parentStub);
  }

  @Override
  public boolean shouldCreateStub(final ASTNode node) {
    if (PsiTreeUtil.getParentOfType(node.getPsi(), PyComprehensionElement.class, true, PyDocStringOwner.class) != null) {
      return false;
    }
    final ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
    final ASTNode qualifierNode = node.findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens());
    if (functionNode != null && qualifierNode != null) {
      final PsiElement function = functionNode.getPsi();
      if (function instanceof PyFunction && PyNames.NEW.equals(((PyFunction)function).getName())) {
        return true;
      }
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
  public void indexStub(@NotNull PyTargetExpressionStub stub, @NotNull IndexSink sink) {
    String name = stub.getName();
    if (name != null && PyUtil.getInitialUnderscores(name) == 0) {
      if (stub.getParentStub() instanceof PyFileStub) {
        sink.occurrence(PyVariableNameIndex.KEY, name);
      }
    }
    for (CustomTargetExpressionStubType stubType : getCustomStubTypes()) {
      stubType.indexStub(stub, sink);
    }
  }
}
