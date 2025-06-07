// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Version;
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
  public @NotNull PsiElement createElement(final @NotNull ASTNode node) {
    return new PyClassImpl(node);
  }

  @Override
  public PyClass createPsi(final @NotNull PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  @Override
  public @NotNull PyClassStub createStub(final @NotNull PyClass psi, final StubElement parentStub) {
    return new PyClassStubImpl(psi.getName(),
                               parentStub,
                               getSuperClassQNames(psi),
                               ContainerUtil.map(psi.getSuperClassExpressions(), PsiElement::getText),
                               PyPsiUtils.asQualifiedName(psi.getMetaClassExpression()),
                               psi.getOwnSlots(),
                               psi.getOwnMatchArgs(),
                               PyPsiUtils.strValue(psi.getDocStringExpression()),
                               psi.getDeprecationMessage(),
                               getStubElementType(),
                               PyVersionSpecificStubBaseKt.evaluateVersionsForElement(psi),
                               createCustomStub(psi));
  }

  public static @NotNull Map<QualifiedName, QualifiedName> getSuperClassQNames(final @NotNull PyClass pyClass) {
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
  public static @NotNull List<PyExpression> getSuperClassExpressions(@NotNull PyClass pyClass) {
    final PyClassStub classStub = pyClass.getStub();
    if (classStub == null) {
      return List.of(pyClass.getSuperClassExpressions());
    }
    return ContainerUtil.mapNotNull(classStub.getSuperClassesText(), 
                                    x -> PyUtil.createExpressionFromFragment(x, pyClass.getContainingFile()));
  }

  private static @Nullable QualifiedName resolveOriginalSuperClassQName(@NotNull PyExpression superClassExpression) {
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
  public void serialize(final @NotNull PyClassStub pyClassStub, final @NotNull StubOutputStream dataStream) throws IOException {
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
    PyFileElementType.writeNullableList(dataStream, pyClassStub.getMatchArgs());

    final String docString = pyClassStub.getDocString();
    dataStream.writeUTFFast(docString != null ? docString : "");
    dataStream.writeName(pyClassStub.getDeprecationMessage());

    PyVersionSpecificStubBaseKt.serializeVersions(pyClassStub.getVersions(), dataStream);

    serializeCustomStub(pyClassStub.getCustomStub(PyCustomClassStub.class), dataStream);
  }

  @Override
  public @NotNull PyClassStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
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
    final List<String> matchArgs = PyFileElementType.readNullableList(dataStream);

    final String docStringInStub = dataStream.readUTFFast();
    final String docString = StringUtil.nullize(docStringInStub);

    final String deprecationMessage = dataStream.readNameString();

    final RangeSet<Version> versions = PyVersionSpecificStubBaseKt.deserializeVersions(dataStream);

    final PyCustomClassStub customStub = deserializeCustomStub(dataStream);

    return new PyClassStubImpl(name, parentStub, superClasses, baseClassesText, metaClass, slots, matchArgs, docString, deprecationMessage,
                               getStubElementType(), versions, customStub);
  }

  @Override
  public void indexStub(final @NotNull PyClassStub stub, final @NotNull IndexSink sink) {
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

    for (PyCustomClassStubType stubType : getExtensions()) {
      stubType.indexStub(stub, sink);
    }
  }

  protected @NotNull IStubElementType getStubElementType() {
    return PyStubElementTypes.CLASS_DECLARATION;
  }

  @Override
  public @NotNull List<PyCustomClassStubType<? extends PyCustomClassStub>> getExtensions() {
    return PyCustomClassStubType.EP_NAME.getExtensionList();
  }
}