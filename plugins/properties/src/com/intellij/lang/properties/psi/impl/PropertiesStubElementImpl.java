/*
 * @author max
 */
package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class PropertiesStubElementImpl <T extends StubElement> extends StubBasedPsiElementBase<T> {
  public PropertiesStubElementImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PropertiesStubElementImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.PROPERTIES.getLanguage();
  }
}