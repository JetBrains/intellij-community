// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass>
  implements PyCustomizableStubElementType<PyClass, PyCustomClassStub, PyCustomClassStubType<? extends PyCustomClassStub>> {

  public PyClassElementType() {
    this("CLASS_DECLARATION");
  }

  public PyClassElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyClassImpl(node);
  }

  @Override
  public PyClass createPsi(@NotNull final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  @Override
  @NotNull
  public PyClassStub createStub(@NotNull final PyClass psi, final StubElement parentStub) {
    return new PyClassStubImpl(psi.getName(),
                               parentStub,
                               getSuperClassQNames(psi),
                               ContainerUtil.map(psi.getSuperClassExpressions(), PsiElement::getText),
                               PyPsiUtils.asQualifiedName(psi.getMetaClassExpression()),
                               psi.getOwnSlots(),
                               PyPsiUtils.strValue(psi.getDocStringExpression()),
                               psi.getDeprecationMessage(),
                               getStubElementType(),
                               PyVersionSpecificStubBaseKt.evaluateVersionRangeForElement(psi),
                               createCustomStub(psi));
  }

  @NotNull
  public static Map<QualifiedName, QualifiedName> getSuperClassQNames(@NotNull final PyClass pyClass) {
    final Map<QualifiedName, QualifiedName> result = new LinkedHashMap<>();

    for (PyExpression expression : PyClassImpl.getUnfoldedSuperClassExpressions(pyClass)) {
      final QualifiedName importedQName = PyPsiUtils.asQualifiedName(expression);
      final QualifiedName originalQName = resolveOriginalSuperClassQName(expression);

      result.put(importedQName, originalQName);
    }

    return result;
  }

  /**
   * If the class' stub is present, return expressions in the base classes list, converting
   * their saved text chunks into {@link PyExpressionCodeFragment} and extracting top-level expressions
   * from them. Otherwise, get superclass expressions directly from AST.
   */
  @NotNull
  public static List<PyExpression> getSuperClassExpressions(@NotNull PyClass pyClass) {
    final PyClassStub classStub = pyClass.getStub();
    if (classStub == null) {
      return List.of(pyClass.getSuperClassExpressions());
    }
    return ContainerUtil.mapNotNull(classStub.getSuperClassesText(), 
                                    x -> PyUtil.createExpressionFromFragment(x, pyClass.getContainingFile()));
  }

  @Nullable
  private static QualifiedName resolveOriginalSuperClassQName(@NotNull PyExpression superClassExpression) {
    if (superClassExpression instanceof PyReferenceExpression reference) {
      final String referenceName = reference.getName();

      if (referenceName == null) {
        return PyPsiUtils.asQualifiedName(superClassExpression);
      }

      final Optional<QualifiedName> qualifiedName = StreamEx.of(PyResolveUtil.resolveLocally(reference))
        .select(PyImportElement.class)
        .filter(element -> element.getAsName() != null)
        .map(PyImportElement::getImportedQName)
        .findAny(Objects::nonNull);

      if (qualifiedName.isPresent()) {
        return qualifiedName.get();
      }
    }

    return PyPsiUtils.asQualifiedName(superClassExpression);
  }

  @Override
  public void serialize(@NotNull final PyClassStub pyClassStub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());

    final Map<QualifiedName, QualifiedName> superClasses = pyClassStub.getSuperClasses();
    dataStream.writeByte(superClasses.size());
    for (Map.Entry<QualifiedName, QualifiedName> entry : superClasses.entrySet()) {
      QualifiedName.serialize(entry.getKey(), dataStream);
      QualifiedName.serialize(entry.getValue(), dataStream);
    }

    final List<String> baseClassesText = pyClassStub.getSuperClassesText();
    dataStream.writeByte(baseClassesText.size());
    for (String text : baseClassesText) {
      dataStream.writeName(text);
    }

    QualifiedName.serialize(pyClassStub.getMetaClass(), dataStream);

    PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());

    final String docString = pyClassStub.getDocString();
    dataStream.writeUTFFast(docString != null ? docString : "");
    dataStream.writeName(pyClassStub.getDeprecationMessage());

    PyVersionSpecificStubBaseKt.serializeVersionRange(pyClassStub.getVersionRange(), dataStream);

    serializeCustomStub(pyClassStub.getCustomStub(PyCustomClassStub.class), dataStream);
  }

  @Override
  @NotNull
  public PyClassStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final String name = dataStream.readNameString();

    final int superClassCount = dataStream.readByte();
    final Map<QualifiedName, QualifiedName> superClasses = new LinkedHashMap<>();
    for (int i = 0; i < superClassCount; i++) {
      superClasses.put(QualifiedName.deserialize(dataStream), QualifiedName.deserialize(dataStream));
    }

    final byte baseClassesCount = dataStream.readByte();
    final ArrayList<String> baseClassesText = new ArrayList<>();
    for (int i = 0; i < baseClassesCount; i++) {
      baseClassesText.add(dataStream.readNameString());
    }


    final QualifiedName metaClass = QualifiedName.deserialize(dataStream);

    final List<String> slots = PyFileElementType.readNullableList(dataStream);

    final String docStringInStub = dataStream.readUTFFast();
    final String docString = StringUtil.nullize(docStringInStub);

    final String deprecationMessage = dataStream.readNameString();

    final PyVersionRange versionRange = PyVersionSpecificStubBaseKt.deserializeVersionRange(dataStream);

    final PyCustomClassStub customStub = deserializeCustomStub(dataStream);

    return new PyClassStubImpl(name, parentStub, superClasses, baseClassesText, metaClass, slots, docString, deprecationMessage,
                               getStubElementType(), versionRange, customStub);
  }

  @Override
  public void indexStub(@NotNull final PyClassStub stub, @NotNull final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
      sink.occurrence(PyClassNameIndexInsensitive.KEY, StringUtil.toLowerCase(name));
      if (stub.getParentStub() instanceof PyFileStub && PyUtil.getInitialUnderscores(name) == 0) {
        sink.occurrence(PyExportedModuleAttributeIndex.KEY, name);
      }
    }

    for (String attribute : PyClassAttributesIndex.getAllDeclaredAttributeNames(stub.getPsi())) {
      sink.occurrence(PyClassAttributesIndex.KEY, attribute);
    }

    stub.getSuperClasses().values()
      .stream()
      .filter(Objects::nonNull)
      .map(QualifiedName::getLastComponent)
      .filter(Objects::nonNull)
      .forEach(className -> sink.occurrence(PySuperClassIndex.KEY, className));
  }

  @NotNull
  protected IStubElementType getStubElementType() {
    return PyStubElementTypes.CLASS_DECLARATION;
  }

  @NotNull
  @Override
  public List<PyCustomClassStubType<? extends PyCustomClassStub>> getExtensions() {
    return PyCustomClassStubType.EP_NAME.getExtensionList();
  }
}