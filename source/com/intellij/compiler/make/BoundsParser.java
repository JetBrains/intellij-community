package com.intellij.compiler.make;

import com.intellij.compiler.classParsing.SignatureParser;
import com.intellij.compiler.classParsing.SignatureParsingException;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 10, 2004
 */
public class BoundsParser extends SignatureParser{
  final List<String> myInterfaceBounds = new ArrayList<String>();
  private int myParsingBound;

  public void parseClassBound(CharacterIterator it, StringBuffer buf) throws SignatureParsingException {
    myParsingBound += 1;
    try {
      super.parseClassBound(it, buf);
    }
    finally {
      myParsingBound -= 1;
    }
  }

  public void parseInterfaceBound(CharacterIterator it, StringBuffer buf) throws SignatureParsingException {
    myParsingBound += 1;
    try {
      super.parseInterfaceBound(it, buf);
    }
    finally {
      myParsingBound -= 1;
    }
  }

  public void parseClassTypeSignature(CharacterIterator it, StringBuffer buf) throws SignatureParsingException {
    if (myParsingBound > 0) {
      final int start = buf.length();

      super.parseClassTypeSignature(it, buf);

      final String qName = convertToQalifiedName(buf.substring(start + 1, buf.length() - 1));
      myInterfaceBounds.add(qName);
    }
    else {
      super.parseClassTypeSignature(it, buf);
    }
  }

  private String convertToQalifiedName(String ifaceSignature) {
    ifaceSignature = ifaceSignature.replaceAll("<.*>", "");
    return ifaceSignature.replace('/', '.');
  }

  public String[] getBounds() {
    return myInterfaceBounds.toArray(new String[myInterfaceBounds.size()]);
  }

  public static String[] getBounds(String classSignature) throws SignatureParsingException {
    final BoundsParser parser = new BoundsParser();
    parser.parseClassSignature(new StringCharacterIterator(classSignature), new StringBuffer());
    return parser.getBounds();
  }
}
