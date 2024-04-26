// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyCustomDecoratorIndexer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyDecoratorImpl;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import com.jetbrains.python.psi.stubs.PyDecoratorStubIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actual serialized data of a decorator call.
 */
public class PyDecoratorCallElementType extends PyStubElementType<PyDecoratorStub, PyDecorator> {
  public PyDecoratorCallElementType() {
    super("DECORATOR_CALL");
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    return new PyDecoratorImpl(node);
  }

  @Override
  public PyDecorator createPsi(@NotNull PyDecoratorStub stub) {
    return new PyDecoratorImpl(stub);
  }

  @Override
  @NotNull
  public PyDecoratorStub createStub(@NotNull PyDecorator psi, StubElement parentStub) {
    PyExpression[] arguments = psi.getArguments();
    List<String> positionalArguments = new ArrayList<>();
    Map<String, String> namedArguments = new HashMap<>();
    for (PyExpression argument : arguments) {
      if (argument instanceof PyKeywordArgument keywordArgument) {
        String keyword = keywordArgument.getKeyword();
        String value = evaluateArgumentValue(keywordArgument);
        if (keyword != null && value != null) {
          namedArguments.put(keyword, value);
        }
      }
      else {
        String value = evaluateArgumentValue(argument);
        positionalArguments.add(value);
      }
    }
    return new PyDecoratorStubImpl(psi.getQualifiedName(), psi.hasArgumentList(), parentStub, ContainerUtil.unmodifiableOrEmptyList(
      positionalArguments), ContainerUtil.unmodifiableOrEmptyMap(namedArguments));
  }

  @Nullable
  @ApiStatus.Internal
  public static String evaluateArgumentValue(@Nullable PyExpression expression) {
    return PyEvaluator.evaluateNoResolve(expression, String.class);
  }

  @Override
  public void serialize(@NotNull PyDecoratorStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    QualifiedName.serialize(stub.getQualifiedName(), dataStream);
    dataStream.writeBoolean(stub.hasArgumentList());
    PyDecoratorStubImpl decoratorStub = (PyDecoratorStubImpl)stub;
    PyFileElementType.writeNullableList(dataStream, decoratorStub.getPositionalArguments());
    dataStream.writeInt(decoratorStub.getKeywordArguments().size());
    for (Map.Entry<String, String> entry : decoratorStub.getKeywordArguments().entrySet()) {
      dataStream.writeName(entry.getKey());
      dataStream.writeName(entry.getValue());
    }
  }

  @Override
  public void indexStub(@NotNull final PyDecoratorStub stub, @NotNull final IndexSink sink) {
    // Index decorators stub by name (todo: index by FQDN as well!)
    final QualifiedName qualifiedName = stub.getQualifiedName();
    if (qualifiedName != null) {
      sink.occurrence(PyDecoratorStubIndex.KEY, qualifiedName.toString());
      PyCustomDecoratorIndexer.EP_NAME.getExtensionList().forEach(extension -> {
        String keyForStub = extension.getKeyForStub(stub);
        if (keyForStub != null) {
          sink.occurrence(extension.getKey(), keyForStub);
        }
      });
    }
  }

  @Override
  @NotNull
  public PyDecoratorStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    QualifiedName q_name = QualifiedName.deserialize(dataStream);
    boolean hasArgumentList = dataStream.readBoolean();
    List<String> positionalArguments = PyFileElementType.readNullableList((dataStream));
    int namedSize = dataStream.readInt();
    Map<String, String> namedArguments = null;
    if (namedSize > 0) {
      namedArguments = new HashMap<>(namedSize);
      for (int i = 0; i < namedSize; i++) {
        String key = dataStream.readNameString();
        String value = dataStream.readNameString();
        namedArguments.put(key, value);
      }
    }
    return new PyDecoratorStubImpl(q_name, hasArgumentList, parentStub, ContainerUtil.notNullize(positionalArguments),
                                   ContainerUtil.notNullize(namedArguments));
  }
}
