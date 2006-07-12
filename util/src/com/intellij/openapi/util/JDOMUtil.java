/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceReader;
import org.jdom.*;
import org.jdom.filter.Filter;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMUtil {
  //Logger is lasy-initialized in order not to use it outside the appClassLoader
  private static Logger ourLogger = null;
  private static SAXBuilder ourSaxBuilder = null;

  private static Logger getLogger() {
    if (ourLogger == null) ourLogger = Logger.getInstance("#com.intellij.openapi.util.JDOMUtil");
    return ourLogger;
  }

  public static final String ENCODING = "UTF-8";

  public static boolean areElementsEqual(Element e1, Element e2) {
    if (e1 == null && e2 == null) return true;
    if (e1 == null) return false;

    if (!attListsEqual(e1.getAttributes(), e2.getAttributes())) {
      return false;
    }
    if (!contentListsEqual(e1.getContent(CONTENT_FILTER), e2.getContent(CONTENT_FILTER))) return false;

    return true;
  }

  private static final EmptyTextFilter CONTENT_FILTER = new EmptyTextFilter();

  public static int getTreeHash(final Element root) {
    return addToHash(0, root);
  }

  private static int addToHash(int i, final Element element) {
    i = addToHash(i, element.getName());
    i = addToHash(i, element.getText());

    final List list = element.getAttributes();
    for (int j = 0; j < list.size(); j++) {
      Attribute attribute = (Attribute)list.get(j);
      i = addToHash(i, attribute);
    }

    final List children = element.getChildren();
    for (int j = 0; j < children.size(); j++) {
      Element child = (Element)children.get(j);
      i = addToHash(i, child);
    }

    return i;
  }

  private static int addToHash(int i, final Attribute attribute) {
    i = addToHash(i, attribute.getName());
    i = addToHash(i, attribute.getValue());
    return i;
  }

  private static int addToHash(final int i, final String s) {
    return i * 31 + s.hashCode();
  }

  private static class EmptyTextFilter implements Filter {
    public boolean matches(Object obj) {
      if (obj instanceof Text) {
        final Text t = (Text)obj;
        return !CharArrayUtil.containsOnlyWhiteSpaces(t.getText());
      }
      return true;
    }
  }

  private static boolean contentListsEqual(final List c1, final List c2) {
    if (c1 == null && c2 == null) return true;
    if (c1 == null) return false;

    Iterator l1 = c1.listIterator();
    Iterator l2 = c2.listIterator();
    while (l1.hasNext() && l2.hasNext()) {
      if (!contentsEqual((Content)l1.next(), (Content)l2.next())) {
        return false;
      }
    }

    return l1.hasNext() == l2.hasNext();
  }

  private static boolean contentsEqual(Content c1, Content c2) {
    if (!(c1 instanceof Element) && !(c2 instanceof Element)) {
      return c1.getValue().equals(c2.getValue());
    }

    if (c1 instanceof Element && c2 instanceof Element) {
      return areElementsEqual((Element)c1, (Element)c2);
    }

    return false;
  }

  private static boolean attListsEqual(List a1, List a2) {
    if (a1.size() != a2.size()) return false;
    for (int i = 0; i < a1.size(); i++) {
      if (!attEqual((Attribute)a1.get(i), (Attribute)a2.get(i))) return false;
    }
    return true;
  }

  private static boolean attEqual(Attribute a1, Attribute a2) {
    return a1.getName().equals(a2.getName()) && a1.getValue().equals(a2.getValue());
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

  public static Document loadDocument(char[] chars, int length) throws IOException, JDOMException {
    SAXBuilder builder = createBuilder();
    return builder.build(new CharArrayReader(chars, 0, length));
  }

  public static Document loadDocument(CharSequence seq) throws IOException, JDOMException {
    SAXBuilder builder = createBuilder();
    return builder.build(new CharSequenceReader(seq));
  }

  public static Document loadDocument(File file) throws JDOMException, IOException {
    SAXBuilder saxBuilder = createBuilder();
    return saxBuilder.build(new InputStreamReader(new BufferedInputStream(new FileInputStream(file)), ENCODING));
  }

  public static Document loadDocument(@NotNull InputStream stream) throws JDOMException, IOException {
    SAXBuilder saxBuilder = createBuilder();
    return saxBuilder.build(new InputStreamReader(stream, ENCODING));
  }

  public static Document loadDocument(URL url) throws JDOMException, IOException {
    SAXBuilder saxBuilder = createBuilder();
    return saxBuilder.build(url);
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
    if (ourSaxBuilder == null) {
      ourSaxBuilder = new SAXBuilder();
      ourSaxBuilder.setEntityResolver(new EntityResolver() {
        public InputSource resolveEntity(String publicId,
                                         String systemId)
          throws SAXException, IOException {
          return new InputSource(new CharArrayReader(ArrayUtil.EMPTY_CHAR_ARRAY));
        }
      });
    }
    return ourSaxBuilder;
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

  public static String writeDocument(Document document, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      writeDocument(document, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException e) {
      // Can't be
      return "";
    }
  }

  public static void writeElement(Element element, Writer writer, String lineSeparator) throws IOException {
    XMLOutputter xmlOutputter = createOutputter(lineSeparator);
    try {
      xmlOutputter.output(element, writer);
    }
    catch (NullPointerException ex) {
      getLogger().error(ex);
      printDiagnostics(element, "");
    }
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
    Format format = Format.getCompactFormat().
      setIndent("  ").
      setTextMode(Format.TextMode.NORMALIZE).
      setEncoding(ENCODING).
      setOmitEncoding(false).
      setOmitDeclaration(false).
      setLineSeparator(lineSeparator);
    xmlOutputter.setFormat(format);
    return xmlOutputter;
  }

  /**
   * Returns null if no escapement necessary.
   */
  @Nullable
  private static String escapeChar(char c, boolean escapeLineEnds) {
    switch (c) {
      case '\n': return escapeLineEnds ? "&#10;" : null;
      case '\r': return escapeLineEnds ? "&#13;" : null;
      case '\t': return escapeLineEnds ? "&#9;" : null;
      case '<':  return "&lt;";
      case '>':  return "&gt;";
      case '\"': return "&quot;";
      case '&':  return "&amp;";
    }
    return null;
  }

  public static String escapeText(String text) {
    return escapeText(text, false);
  }

  private static String escapeText(String text, boolean escapeLineEnds) {
    StringBuffer buffer = null;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      final String quotation = escapeChar(ch, escapeLineEnds);

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

  public static List<Element> getChildrenFromAllNamespaces(final Element element, @NonNls final String name) {
    final ArrayList<Element> result = new ArrayList<Element>();
    final List children = element.getChildren();
    for (final Object aChildren : children) {
      Element child = (Element)aChildren;
      if (name.equals(child.getName())) {
        result.add(child);
      }
    }
    return result;
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
