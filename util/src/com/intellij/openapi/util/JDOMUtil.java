/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntObjectHashMap;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class JDOMUtil {
  //Logger is lasy-initialized in order not to use it outside the appClassLoader
  private static Logger ourLogger = null;

  private static Logger getLogger() {
    if (ourLogger == null) ourLogger = Logger.getInstance("#com.intellij.openapi.util.JDOMUtil");
    return ourLogger;
  }

  public static final String ENCODING = "UTF-8";

  public static boolean areElementsEqual(Element e1, Element e2) {
    Document d1 = new Document((Element)e1.clone());
    Document d2 = new Document((Element)e2.clone());

    return areDocumentsEqual(d1, d2);
  }

  public static boolean areDocumentsEqual(Document d1, Document d2) {
    if(d1.hasRootElement() != d2.hasRootElement()) return false;

    if(!d1.hasRootElement()) return true;

    CharArrayWriter w1 = new CharArrayWriter();
    CharArrayWriter w2 = new CharArrayWriter();

    try {
      writeDocument(d1, w1, "\n");
      writeDocument(d2, w2, "\n");
    }
    catch (IOException e) {
      getLogger().error(e);
    }

    if (w1.size() != w2.size()) return false;

    return w1.toString().equals(w2.toString());
  }


  public static Document loadDocument(File file) throws JDOMException, IOException {
    SAXBuilder saxBuilder = createBuilder();
    return saxBuilder.build(new InputStreamReader(new FileInputStream(file), ENCODING));
  }

  public static Document loadDocument(InputStream stream) throws JDOMException, IOException {
    SAXBuilder saxBuilder = createBuilder();
    return saxBuilder.build(new InputStreamReader(stream, ENCODING));
  }

  public static void writeDocument(Document document, String filePath, String lineSeparator) throws IOException {
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(filePath));
    try {
      writeDocument(document, stream, lineSeparator);
    }
    finally {
      stream.close();
    }
  }

  private static SAXBuilder createBuilder() {
    final SAXBuilder saxBuilder = new SAXBuilder();
    saxBuilder.setEntityResolver(new EntityResolver() {
      public InputSource resolveEntity(String publicId,
                                       String systemId)
        throws SAXException, IOException {
        return new InputSource(new CharArrayReader(new char[0]));
      }
    });
    return saxBuilder;
  }

  public static void writeDocument(Document document, File file, String lineSeparator) throws IOException {
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
    try {
      writeDocument(document, stream, lineSeparator);
    }
    finally {
      stream.close();
    }
  }

  public static void writeDocument(Document document, OutputStream stream, String lineSeparator) throws IOException {
    writeDocument(document, new OutputStreamWriter(stream, ENCODING), lineSeparator);
  }


  public static byte[] printDocument(Document document, String lineSeparator) throws UnsupportedEncodingException, IOException {
    CharArrayWriter writer = new CharArrayWriter();
    writeDocument(document, writer, lineSeparator);

    return new String(writer.toCharArray()).getBytes(ENCODING);
  }

  public static void writeDocument(Document document, Writer writer, String lineSeparator) throws IOException {
    XMLOutputter xmlOutputter = createOutputter(lineSeparator);
    try {
      xmlOutputter.output(document, writer);
      writer.close();
    }
    catch (NullPointerException ex) {
      getLogger().error(ex);
      printDiagnostics(document.getRootElement(), "");
    }
  }

  public static XMLOutputter createOutputter(String lineSeparator) {
    XMLOutputter xmlOutputter = new MyXMLOutputter();
    xmlOutputter.setIndent("  ");
    xmlOutputter.setNewlines(true);
//    xmlOutputter.setTextTrim(true);
    xmlOutputter.setTextNormalize(true);
    xmlOutputter.setEncoding(ENCODING);
    xmlOutputter.setOmitEncoding(false);
    xmlOutputter.setOmitDeclaration(false);
    xmlOutputter.setLineSeparator(lineSeparator);
    return xmlOutputter;
  }

  private static final TIntObjectHashMap ourCharToQuotationMap = new TIntObjectHashMap();
  static {
    ourCharToQuotationMap.put('<', "&lt;");
    ourCharToQuotationMap.put('>', "&gt;");
    //ourCharToQuotationMap.put('\'', "&apos;");
    ourCharToQuotationMap.put('\"', "&quot;");
    ourCharToQuotationMap.put('&', "&amp;");
  }

  public static String escapeText(String text) {
    return escapeText(text, false);
  }
  
  private static String escapeText(String text, boolean escapeLineEnds) {
    StringBuffer buffer = null;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      final String quotation;
      if (escapeLineEnds) {
        switch (ch) {
          case '\n': quotation = "&#10;"; break;
          case '\r': quotation = "&#13;"; break;
          case '\t': quotation = "&#9;"; break;
          default : quotation = (String)ourCharToQuotationMap.get(ch); break;
        }
      }
      else {
        quotation = (String)ourCharToQuotationMap.get(ch);
      }
      if (buffer == null) {
        if (quotation != null) {
          // An quotation occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuffer(text.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(text.substring(0, i));
          buffer.append(quotation);
        }
      }
      else {
        if (quotation == null) {
          buffer.append(ch);
        }
        else {
          buffer.append(quotation);
        }
      }
    }
    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return (buffer == null) ? text : buffer.toString();
  }

  public static class MyXMLOutputter extends XMLOutputter {
    public String escapeAttributeEntities(String str) {
      return escapeText(str, true);
    }

    public String escapeElementEntities(String str) {
      return escapeText(str, false);
    }
  }

  private static void printDiagnostics(Element element, String prefix) {
    ElementInfo info = getElementInfo(element);
    prefix = prefix + "/" + info.name;
    if (info.hasNullAttributes) {
      System.err.println(prefix);
    }

    List children = element.getChildren();
    for (Iterator i = children.iterator(); i.hasNext();) {
      Element e = (Element)i.next();
      printDiagnostics(e, prefix);
    }
  }

  private static ElementInfo getElementInfo(Element element) {
    ElementInfo info = new ElementInfo();
    StringBuffer buf = new StringBuffer(element.getName());
    List attributes = element.getAttributes();
    if (attributes != null) {
      int length = attributes.size();
      if (length > 0) {
        buf.append("[");
        for (int idx = 0; idx < length; idx++) {
          Attribute attr = (Attribute)attributes.get(idx);
          if (idx != 0) {
            buf.append(";");
          }
          buf.append(attr.getName());
          buf.append("=");
          buf.append(attr.getValue());
          if (attr.getValue() == null) {
            info.hasNullAttributes = true;
          }
        }
        buf.append("]");
      }
    }
    info.name = buf.toString();
    return info;
  }

  public static void updateFileSet(File[] oldFiles, String[] newFilePaths, Document[] newFileDocuments, String lineSeparator)
    throws IOException {
    getLogger().assertTrue(newFilePaths.length == newFileDocuments.length);

    ArrayList writtenFilesPaths = new ArrayList();

    // check if files are writable
    for (int i = 0; i < newFilePaths.length; i++) {
      String newFilePath = newFilePaths[i];
      File file = new File(newFilePath);
      if (file.exists() && !file.canWrite()) {
        throw new IOException("File \"" + newFilePath + "\" is not writeable");
      }
    }
    for (int i = 0; i < oldFiles.length; i++) {
      File file = oldFiles[i];
      if (file.exists() && !file.canWrite()) {
        throw new IOException("File \"" + file.getAbsolutePath() + "\" is not writeable");
      }
    }

    for (int i = 0; i < newFilePaths.length; i++) {
      String newFilePath = newFilePaths[i];

      JDOMUtil.writeDocument(newFileDocuments[i], newFilePath, lineSeparator);
      writtenFilesPaths.add(newFilePath);
    }

    // delete files if necessary

    outer: for (int i = 0; i < oldFiles.length; i++) {
      File oldFile = oldFiles[i];
      String oldFilePath = oldFile.getAbsolutePath();
      for (Iterator iterator = writtenFilesPaths.iterator(); iterator.hasNext();) {
        if (oldFilePath.equals(iterator.next())) {
          continue outer;
        }
      }
      boolean result = oldFile.delete();
      if (!result) {
        throw new IOException("File \"" + oldFilePath + "\" was not deleted");
      }
    }
  }

  private static class ElementInfo {
    public String name = "";
    public boolean hasNullAttributes = false;
  }
}
