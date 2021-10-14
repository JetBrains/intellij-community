// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decorator stub storage.
 * User: dcheryasov
 */
public class PyDecoratorStubImpl extends StubBase<PyDecorator> implements PyDecoratorStub {
  private final QualifiedName myQualifiedName;
  private final List<@Nullable String> myPositionalArguments;
  private final Map<String, @Nullable String> myKeywordArguments;

  protected PyDecoratorStubImpl(final QualifiedName qualname,
                                final StubElement parent,
                                final List<String> positionalArguments,
                                final Map<String, String> namedArguments) {
    super(parent, PyElementTypes.DECORATOR_CALL);
    myQualifiedName = qualname;
    myPositionalArguments = positionalArguments != null ? positionalArguments : Collections.emptyList();
    myKeywordArguments = namedArguments != null ? namedArguments : Collections.emptyMap();
  }

  @Override
  public QualifiedName getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public boolean hasArgumentList() {
    return myPositionalArguments.size() > 0 || myKeywordArguments.size() > 0;
  }

  @Override
  public @Nullable String getPositionalArgumentLiteralText(int position) {
    if (position >= myPositionalArguments.size()) return null;
    return myPositionalArguments.get(position);
  }

  @Override
  public @Nullable String getNamedArgumentLiteralText(@NotNull String name) {
    return myKeywordArguments.get(name);
  }

  protected List<String> getPositionalArguments() {
    return myPositionalArguments;
  }

  protected Map<String, String> getKeywordArguments() {
    return myKeywordArguments;
  }
}
