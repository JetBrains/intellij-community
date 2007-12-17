package com.intellij.psi.impl.source;

import com.intellij.psi.impl.source.tree.FileElement;

public class DummyHolderElement extends FileElement {
  public DummyHolderElement() {
    super(Constants.DUMMY_HOLDER);
  }
}
