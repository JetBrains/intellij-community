package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.util.CharTable;

/**
 * @author ven
 */
public class XmlParsingContext extends ParsingContext {
  protected final XmlParsing myXmlParsing;

  public XmlParsing getXmlParsing() {
    return myXmlParsing;
  }

  public XmlParsingContext(final CharTable table) {
    super(table);
    myXmlParsing = new XmlParsing(this);
  }
}
