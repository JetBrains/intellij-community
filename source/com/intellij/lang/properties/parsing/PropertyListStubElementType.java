/*
 * @author max
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.lang.properties.psi.impl.PropertiesListImpl;
import com.intellij.lang.properties.psi.impl.PropertiesListStubImpl;
import com.intellij.psi.stubs.*;

import java.io.IOException;

public class PropertyListStubElementType extends IStubElementType<PropertiesListStub, PropertiesList> {
  public PropertyListStubElementType() {
    super("PROPERTIES_LIST", PropertiesElementTypes.LANG);
  }

  public PropertiesList createPsi(final PropertiesListStub stub) {
    return new PropertiesListImpl(stub);
  }

  public PropertiesListStub createStub(final PropertiesList psi, final StubElement parentStub) {
    return new PropertiesListStubImpl(parentStub);
  }

  public String getExternalId() {
    return "properties.propertieslist";
  }

  public void serialize(final PropertiesListStub stub, final StubOutputStream dataStream) throws IOException {
  }

  public PropertiesListStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PropertiesListStubImpl(parentStub);
  }

  public void indexStub(final PropertiesListStub stub, final IndexSink sink) {
  }
}