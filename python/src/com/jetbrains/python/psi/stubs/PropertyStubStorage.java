package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.PropertyBunch;
import com.jetbrains.python.psi.impl.PyQualifiedName;
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
 * Date: Jun 3, 2010 6:46:01 AM
 */
public class PropertyStubStorage extends PropertyBunch<String> implements CustomTargetExpressionStub {

  @Override
  protected String translate(@NotNull PyReferenceExpression ref) {
    final String name = ref.getName();
    assert name != null;
    return name;
  }

  @NotNull
  @Override
  public Maybe<String> getGetter() {
    return myGetter;
  }

  @NotNull
  @Override
  public Maybe<String> getSetter() {
    return mySetter;
  }

  @NotNull
  @Override
  public Maybe<String> getDeleter() {
    return myDeleter;
  }

  public String getDoc() {
    return myDoc;
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
  public PyQualifiedName getCalleeName() {
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

  private static final Maybe<String> unknown = new Maybe<String>();
  private static final Maybe<String> none = new Maybe<String>(null);

  @Nullable
  private static Maybe<String> readOne(StubInputStream stream) throws IOException {
    StringRef ref = stream.readName();
    if (ref == null) return none;
    else {
      String s = ref.getString();
      if (IMPOSSIBLE_NAME.equals(s)) return unknown;
      else return new Maybe<String>(s);
    }
  }

  @Nullable
  public static PropertyStubStorage fromCall(@Nullable PyExpression expr) {
    final PropertyStubStorage prop = new PropertyStubStorage();
    final boolean success = fillFromCall(expr, prop);
    return success? prop : null;
  }

}
