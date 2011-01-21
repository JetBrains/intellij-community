package com.jetbrains.python.validation;

import com.jetbrains.python.psi.LanguageLevel;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.CharArrayWriter;
import java.util.HashSet;

/**
 * User: catherine
 */
public class VersionsParser extends DefaultHandler {

  private CharArrayWriter myContent = new CharArrayWriter();
  private LanguageLevel myCurrentLevel;

  public void startElement(String namespaceURI,
              String localName,
              String qName,
              Attributes attr) throws SAXException {
    myContent.reset();
    if ( localName.equals( "python" ) ) {
      UnsupportedFeaturesUtil.BUILTINS.put(LanguageLevel.fromPythonVersion(attr.getValue("version")), new HashSet<String>());
      UnsupportedFeaturesUtil.MODULES.put(LanguageLevel.fromPythonVersion(attr.getValue("version")), new HashSet<String>());
      myCurrentLevel = LanguageLevel.fromPythonVersion(attr.getValue("version"));
    }
   }

  public void endElement(String namespaceURI,
            String localName,
            String qName) throws SAXException {
    if (localName.equals("func")) {
      UnsupportedFeaturesUtil.BUILTINS.get(myCurrentLevel).add(myContent.toString());
    }
    if (localName.equals("module")) {
      UnsupportedFeaturesUtil.MODULES.get(myCurrentLevel).add(myContent.toString());
    }
  }

  public void characters(char[] ch, int start, int length)
                                        throws SAXException {
    myContent.write(ch, start, length);
  }
}

