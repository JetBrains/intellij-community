/*
 * @author max
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKeyIndex;
import com.intellij.lang.properties.psi.PropertyStub;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyStubImpl;
import com.intellij.psi.stubs.*;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class PropertyStubElementType extends IStubElementType<PropertyStub, Property> {
  public PropertyStubElementType() {
    super("PROPERTY", PropertiesElementTypes.LANG);
  }

  public Property createPsi(final PropertyStub stub) {
    return new PropertyImpl(stub, this);
  }

  public PropertyStub createStub(final Property psi, final StubElement parentStub) {
    return new PropertyStubImpl(parentStub, psi.getKey());
  }

  public String getExternalId() {
    return "properties.prop";
  }

  public void serialize(final PropertyStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getKey());
  }

  public PropertyStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final StringRef ref = dataStream.readName();
    return new PropertyStubImpl(parentStub, ref.getString());
  }

  public void indexStub(final PropertyStub stub, final IndexSink sink) {
    sink.occurrence(PropertyKeyIndex.KEY, PropertyImpl.unescape(stub.getKey()));
  }
}