package com.jetbrains.python.debugger;

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
import java.util.List;

/**
 * Translate XML protocol responses into Py structures.
 *
 */
public class PydevXmlUtils {

  static SAXParserFactory parserFactory = SAXParserFactory.newInstance();

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

  /**
   * Processes CMD_GET_COMPLETIONS return
   */
  static class XMLToCompletionsInfo extends DefaultHandler {
    private List<Object[]> completions;

    public XMLToCompletionsInfo() {
      completions = new ArrayList<Object[]>();
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


  public static List<Object[]> xmlToCompletions(String payload) throws Exception {
    SAXParser parser = getSAXParser();
    XMLToCompletionsInfo info = new XMLToCompletionsInfo();
    parser.parse(new ByteArrayInputStream(payload.getBytes()), info);
    return info.getCompletions();
  }
}

