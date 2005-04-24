package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;

/**
 * @author ven
 */
public class ParsingContext {
  protected final CharTable myTable;
  
  public ParsingContext(final CharTable table) {
    myTable = table;
  }

  public CharTable getCharTable() {
    return myTable;
  }
}
