package com.jetbrains.python.debugger;

import com.jetbrains.python.console.pydev.AbstractPyCodeCompletion;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Translate XML protocol responses into Py structures.
 *
 */
public class PydevXmlUtils {

  static SAXParserFactory parserFactory = SAXParserFactory.newInstance();

  private PydevXmlUtils() {
  }

  static SAXParser getSAXParser() throws Exception {
    SAXParser parser = null;

    synchronized (parserFactory) {
      parser = parserFactory.newSAXParser();
    }

    return parser;
  }

  @Nullable
  private static String decode(String value) {
    if (value != null) {
      try {
        return URLDecoder.decode(value, "UTF-8");
      }
      catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  public static List<PydevCompletionVariant> decodeCompletions(Object fromServer, String actTok) {
    final List<PydevCompletionVariant> ret = new ArrayList<>();

    List completionList = objectToList(fromServer);

    for (Object o : completionList) {
      List comp = objectToList(o);

      //name, doc, args, type
      final int type = extractInt(comp.get(3));
      final String args = AbstractPyCodeCompletion.getArgs((String)comp.get(2), type,
                                                           AbstractPyCodeCompletion.LOOKING_FOR_INSTANCED_VARIABLE);

      String name = (String)comp.get(0);

      if (name.contains(".") && name.startsWith(actTok)) {
        name = name.substring(actTok.length());
      }

      ret.add(new PydevCompletionVariant(name, (String)comp.get(1), args, type));
    }
    return ret;
  }

  public static List objectToList(Object object) {
    List list;
    if (object instanceof Collection) {
      list = new ArrayList((Collection)object);
    }
    else if (object instanceof Object[]) {
      list = Arrays.asList((Object[])object);
    }
    else {
      throw new IllegalStateException("cant handle type of " + object);
    }
    return list;
  }

  /**
   * Extracts an int from an object
   *
   * @param objToGetInt the object that should be gotten as an int
   * @return int with the int the object represents
   */
  public static int extractInt(Object objToGetInt) {
    if (objToGetInt instanceof Integer) {
      return (Integer)objToGetInt;
    }
    return Integer.parseInt(objToGetInt.toString());
  }

  /**
   * Processes CMD_GET_COMPLETIONS return
   */
  static class XMLToCompletionsInfo extends DefaultHandler {
    private List<Object[]> completions;

    public XMLToCompletionsInfo() {
      completions = new ArrayList<>();
    }

    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
      // <comp p0="%s" p1="%s" p2="%s" p3="%s"/>
      if (qName.equals("comp")) {

        Object[] comp = new Object[]{
          decode(attributes.getValue("p0")),
          decode(attributes.getValue("p1")),
          decode(attributes.getValue("p2")),
          decode(attributes.getValue("p3")),
        };

        completions.add(comp);
      }
    }

    public List<Object[]> getCompletions() {
      return completions;
    }
  }


  public static List<PydevCompletionVariant> xmlToCompletions(String payload, String actionToken) throws Exception {
    SAXParser parser = getSAXParser();
    XMLToCompletionsInfo info = new XMLToCompletionsInfo();
    parser.parse(new ByteArrayInputStream(payload.getBytes()), info);
    return decodeCompletions(info.getCompletions(), actionToken);
  }
}

