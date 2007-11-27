package com.intellij.psi.formatter.xml;

import com.intellij.formatting.Block;

public interface ReadOnlyBlockInformationProvider {
  boolean isReadOnly(Block block);
}
