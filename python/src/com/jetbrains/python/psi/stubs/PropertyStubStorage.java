/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.impl.PropertyBunch;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStubType;
import com.jetbrains.python.psi.impl.stubs.PropertyStubType;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Packs property description for storage in a stub.
 * User: dcheryasov
 */
public class PropertyStubStorage extends PropertyBunch<String> implements CustomTargetExpressionStub {

  @NotNull
  @Override
  protected Maybe<String> translate(@Nullable PyExpression ref) {
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

  @NotNull
  @Override
  public Class<? extends CustomTargetExpressionStubType> getTypeClass() {
    return PropertyStubType.class;
  }

  public void serialize(StubOutputStream stream) throws IOException {
    writeOne(myGetter, stream);
    writeOne(mySetter, stream);
    writeOne(myDeleter, stream);
    stream.writeName(myDoc);
  }

  @Override
  public QualifiedName getCalleeName() {
    return null;  // ??
  }

  public static PropertyStubStorage deserialize(StubInputStream stream) throws IOException {
    PropertyStubStorage me = new PropertyStubStorage();
    me.myGetter  = readOne(stream);
    me.mySetter  = readOne(stream);
    me.myDeleter = readOne(stream);
    //
    StringRef ref = stream.readName();
    me.myDoc = ref != null? ref.getString() : null;
    return me;
  }

  private static final Maybe<String> unknown = new Maybe<>();
  private static final Maybe<String> none = new Maybe<>(null);

  @Nullable
  private static Maybe<String> readOne(StubInputStream stream) throws IOException {
    StringRef ref = stream.readName();
    if (ref == null) return none;
    else {
      String s = ref.getString();
      if (IMPOSSIBLE_NAME.equals(s)) return unknown;
      else return new Maybe<>(s);
    }
  }

  @Nullable
  public static PropertyStubStorage fromCall(@Nullable PyExpression expr) {
    final PropertyStubStorage prop = new PropertyStubStorage();
    final boolean success = fillFromCall(expr, prop);
    return success? prop : null;
  }

}
