// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.impl.PropertyBunch;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.impl.stubs.PropertyStubType;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Packs property description for storage in a stub.
 */
public class PropertyStubStorage extends PropertyBunch<String> implements CustomTargetExpressionStub {

  @Override
  protected @NotNull Maybe<String> translate(@Nullable PyExpression ref) {
    if (ref instanceof PyNoneLiteralExpression) {
      return new Maybe<>(PyNames.NONE);
    }
    else if (ref != null) {
      final String name = ref.getName();
      return name != null ? new Maybe<>(name) : unknown;
    }
    return none;
  }

  private static final String IMPOSSIBLE_NAME = "#";

  private static void writeOne(Maybe<String> what, StubOutputStream stream) throws IOException {
    if (what.isDefined()) stream.writeName(what.value());
    else stream.writeName(IMPOSSIBLE_NAME);
  }

  @Override
  public @NotNull Class<PropertyStubType> getTypeClass() {
    return PropertyStubType.class;
  }

  @Override
  public void serialize(@NotNull StubOutputStream stream) throws IOException {
    writeOne(myGetter, stream);
    writeOne(mySetter, stream);
    writeOne(myDeleter, stream);
    stream.writeName(myDoc);
  }

  @Override
  public QualifiedName getCalleeName() {
    return null;  // ??
  }

  @Override
  public String toString() {
    return "PropertyStubStorage(" +
           "getter=" + myGetter.valueOrNull() +
           ", setter=" + mySetter.valueOrNull() +
           ", deleter=" + myDeleter.valueOrNull() +
           ", doc=" + (myDoc != null ? "'" + StringUtil.escapeStringCharacters(myDoc) + "'" : null) +
           ')';
  }

  public static PropertyStubStorage deserialize(StubInputStream stream) throws IOException {
    PropertyStubStorage me = new PropertyStubStorage();
    me.myGetter  = readOne(stream);
    me.mySetter  = readOne(stream);
    me.myDeleter = readOne(stream);
    //
    me.myDoc = stream.readNameString();
    return me;
  }

  private static final Maybe<String> unknown = new Maybe<>();
  private static final Maybe<String> none = new Maybe<>(null);

  private static @Nullable Maybe<String> readOne(StubInputStream stream) throws IOException {
    String s = stream.readNameString();
    if (s == null) return none;
    else {
      if (IMPOSSIBLE_NAME.equals(s)) return unknown;
      else return new Maybe<>(s);
    }
  }

  public static @Nullable PropertyStubStorage fromCall(@Nullable PyExpression expr) {
    final PropertyStubStorage prop = new PropertyStubStorage();
    final boolean success = fillFromCall(expr, prop);
    return success? prop : null;
  }

}
