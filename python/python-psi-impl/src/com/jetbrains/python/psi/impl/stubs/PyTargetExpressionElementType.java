// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;


public class PyTargetExpressionElementType extends PyStubElementType<PyTargetExpressionStub, PyTargetExpression>
  implements
  PyCustomizableStubElementType<PyTargetExpression, CustomTargetExpressionStub, CustomTargetExpressionStubType<? extends CustomTargetExpressionStub>> {

  public PyTargetExpressionElementType() {
    super("TARGET_EXPRESSION");
  }

  public PyTargetExpressionElementType(@NotNull @NonNls String debugName) {
    super(debugName);
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
    final PyVersionRange versionRange = PyVersionSpecificStubBaseKt.evaluateVersionRangeForElement(psi);

    CustomTargetExpressionStub customStub = createCustomStub(psi);
    if (customStub != null) {
      return new PyTargetExpressionStubImpl(name, docString, typeComment, annotation, psi.hasAssignedValue(), customStub, parentStub,
                                            versionRange);
    }

    PyTargetExpressionStub.InitializerType initializerType = PyTargetExpressionStub.InitializerType.Other;
    QualifiedName initializer = null;
    final Ref<QualifiedName> assignedReference = PyTargetExpressionImpl.getAssignedReferenceQualifiedName(psi);
    if (assignedReference != null) {
      initializerType = PyTargetExpressionStub.InitializerType.ReferenceExpression;
      initializer = assignedReference.get();
    }
    else {
      final Ref<QualifiedName> assignedCallCalleeReference = PyTargetExpressionImpl.getAssignedCallCalleeQualifiedName(psi);
      if (assignedCallCalleeReference != null) {
        initializerType = PyTargetExpressionStub.InitializerType.CallExpression;
        initializer = assignedCallCalleeReference.get();
      }
    }
    return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, psi.isQualified(), typeComment, annotation,
                                          psi.hasAssignedValue(), parentStub, versionRange);
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
    PyVersionSpecificStubBaseKt.serializeVersionRange(stub.getVersionRange(), stream);
    final CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
    if (customStub != null) {
      serializeCustomStub(customStub, stream);
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
    PyVersionRange versionRange = PyVersionSpecificStubBaseKt.deserializeVersionRange(stream);
    if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
      CustomTargetExpressionStub stub = deserializeCustomStub(stream);
      return new PyTargetExpressionStubImpl(name, docString, typeComment, annotation, hasAssignedValue, stub, parentStub, versionRange);
    }
    QualifiedName initializer = QualifiedName.deserialize(stream);
    boolean isQualified = stream.readBoolean();
    return new PyTargetExpressionStubImpl(name, docString, initializerType, initializer, isQualified, typeComment, annotation,
                                          hasAssignedValue, parentStub, versionRange);
  }

  @Override
  public boolean shouldCreateStub(final ASTNode node) {
    if (node.getTreeParent().getElementType() != PyElementTypes.ASSIGNMENT_EXPRESSION &&
        PsiTreeUtil.getParentOfType(node.getPsi(), PyComprehensionElement.class, true, PyDocStringOwner.class) != null) {
      return false;
    }
    final ASTNode functionNode = TreeUtil.findParent(node, PyElementTypes.FUNCTION_DECLARATION);
    final ASTNode qualifierNode = node.findChildByType(PythonDialectsTokenSetProvider.getInstance().getReferenceExpressionTokens());
    if (functionNode != null && qualifierNode != null) {
      if (PyUtil.isNewMethod(functionNode.getPsi())) {
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
        sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
      }
    }
    for (CustomTargetExpressionStubType stubType : getExtensions()) {
      stubType.indexStub(stub, sink);
    }
  }

  @NotNull
  @Override
  public List<CustomTargetExpressionStubType<? extends CustomTargetExpressionStub>> getExtensions() {
    return CustomTargetExpressionStubType.EP_NAME.getExtensionList();
  }
}
