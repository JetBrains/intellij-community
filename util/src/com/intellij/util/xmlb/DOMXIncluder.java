package com.intellij.util.xmlb;

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.DOMUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DOMXIncluder {

  @NonNls public final static String XINCLUDE_NAMESPACE = "http://www.w3.org/2001/XInclude";
  @NonNls public final static String XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String HREF = "href";
  @NonNls private static final String BASE = "base";
  @NonNls private static final String PARSE = "parse";
  @NonNls private static final String TEXT = "text";
  @NonNls private static final String XPOINTER = "xpointer";

  // No instances allowed
  private DOMXIncluder() {
  }

  public static Document resolve(@NotNull Document original, String base) throws XIncludeException, NullPointerException {
    Document resultDocument = (Document)original.cloneNode(true);

    Element resultRoot = resultDocument.getDocumentElement();

    NodeList resolved = resolve(resultRoot, base, resultDocument);

    int numberRoots = 0;
    for (int i = 0; i < resolved.getLength(); i++) {
      if (resolved.item(i) instanceof Comment || resolved.item(i) instanceof ProcessingInstruction ||
          resolved.item(i) instanceof DocumentType) {
      }
      else if (resolved.item(i) instanceof Element) {
        numberRoots++;
      }
      else if (resolved.item(i) instanceof Text) {
        throw new XIncludeException("Tried to include text node outside document element");
      }
      else {
        throw new XIncludeException("Cannot include a " + resolved.item(i).getNodeType() + " node");
      }
    }

    if (numberRoots != 1) {
      throw new XIncludeException("Tried to include multiple roots");
    }

    // insert nodes before the root
    int nodeIndex = 0;
    while (nodeIndex < resolved.getLength()) {
      if (resolved.item(nodeIndex) instanceof Element) break;
      resultDocument.insertBefore(resolved.item(nodeIndex), resultRoot);
      nodeIndex++;
    }

    // insert new root
    resultDocument.replaceChild(resolved.item(nodeIndex), resultRoot);
    nodeIndex++;

    //insert nodes after new root
    Node refNode = resultDocument.getDocumentElement().getNextSibling();
    if (refNode == null) {
      while (nodeIndex < resolved.getLength()) {
        resultDocument.appendChild(resolved.item(nodeIndex));
        nodeIndex++;
      }
    }
    else {
      while (nodeIndex < resolved.getLength()) {
        resultDocument.insertBefore(resolved.item(nodeIndex), refNode);
        nodeIndex++;
      }
    }

    return resultDocument;

  }

  public static NodeList resolve(@NotNull Element original, String base, Document resolved) throws XIncludeException, NullPointerException {
    Stack<String> bases = new Stack<String>();
    if (base != null) bases.push(base);

    NodeList result = resolve(original, bases, resolved);
    bases.pop();
    return result;

  }

  private static boolean isIncludeElement(Element element) {
    if (element.getLocalName().equals(INCLUDE) && element.getNamespaceURI().equals(XINCLUDE_NAMESPACE)) {
      return true;
    }
    return false;
  }


  private static NodeList resolve(Element original, Stack<String> bases, Document resolved) throws XIncludeException {
    XIncludeNodeList result = new XIncludeNodeList();
    String base = null;
    if (bases.size() != 0) base = bases.peek();

    if (isIncludeElement(original)) {
      resolveInclude(original, base, bases, result, resolved);
    }
    else {
      Element copy = (Element)resolved.importNode(original, false);
      NodeList children = original.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node n = children.item(i);
        if (n instanceof Element) {
          Element e = (Element)n;
          NodeList kids = resolve(e, bases, resolved);
          for (int j = 0; j < kids.getLength(); j++) {
            copy.appendChild(copy.getOwnerDocument().adoptNode(kids.item(j)));
          }
        }
        else {
          copy.appendChild(resolved.importNode(n, true));
        }
      }
      result.add(copy);
    }

    return result;

  }

  private static void resolveInclude(final Element original, String base, final Stack<String> bases, final XIncludeNodeList result,
                                     final Document resolved) {
    // Verify that there is an href attribute
    if (!original.hasAttribute(HREF)) {
      throw new XIncludeException("Missing href attribute");
    }
    String href = original.getAttribute(HREF);

    // Check for a base attribute
    String baseAttribute = original.getAttributeNS(XML_NAMESPACE, BASE);
    if (baseAttribute != null && baseAttribute.length() != 0) {
      base = baseAttribute;
    }

    String remote;
    if (base != null) {
      try {
        URL context = new URL(base);
        URL u = new URL(context, href);
        remote = u.toExternalForm();
      }
      catch (MalformedURLException ex) {
        throw new XIncludeException("Unresolvable URL " + base + "/" + href, ex);
      }
    }
    else {
      remote = href;
    }

    // check for parse attribute; default is true
    boolean parse = true;
    if (original.hasAttribute(PARSE)) {
      String parseAttribute = original.getAttribute(PARSE);
      if (parseAttribute.equals(TEXT)) {
        parse = false;
      }
    }

    if (parse) {
      // checks for equality (OK) or identity (not OK)????
      if (bases.contains(remote)) {
        // need to figure out how to get file and number where
        // bad include occurs????
        throw new XIncludeException("Circular XInclude Reference to " + remote + " in ");
      }

      try {
        bases.push(remote);

        incorporateInclude(remote, original, result, bases, resolved);

        bases.pop();
      }
      // Make this configurable
      catch (SAXException e) {
        throw new XIncludeException(e);
      }
      catch (IOException e) {
        throw new XIncludeException(e);
      }
      catch (ParserConfigurationException e) {
        throw new XIncludeException(e);
      }
    }
    else { // insert text
      String s = downloadTextDocument(remote);
      result.add(resolved.createTextNode(s));
    }
  }

  private static void incorporateInclude(final String remote, final Element original, final XIncludeNodeList result, final Stack<String> bases,
                                 final Document resolved) throws IOException, ParserConfigurationException, SAXException {
    Document doc = DOMUtil.load(new URL(remote));
    NodeList docChildren = extractNeededChildren(original, doc);
    if (docChildren == null) return;
    for (int i = 0; i < docChildren.getLength(); i++) {
      Node node = docChildren.item(i);
      if (node instanceof Element) {
        result.add(resolve((Element)node, bases, resolved));
      }
      else if (node instanceof DocumentType) {
      }
      else {
        result.add(node);
      }
    }
  }

  //xpointer($1)
  @NonNls private static Pattern XPOINTER_PATTERN = Pattern.compile("xpointer\\((.*)\\)");

  // /$1/*
  private static Pattern CHILDREN_PATTERN = Pattern.compile("\\/(.*)\\/\\*");

  @Nullable
  private static NodeList extractNeededChildren(final Element original, final Document doc) {
    if (original.hasAttribute(XPOINTER)) {
      final String pointerAttr = original.getAttribute(XPOINTER);

      Matcher matcher = XPOINTER_PATTERN.matcher(pointerAttr);
      if (matcher.matches()) {
        String pointer = matcher.group(1);

        matcher = CHILDREN_PATTERN.matcher(pointer);

        if (matcher.matches()) {
          final String rootTagName = matcher.group(1);
          if (doc.getDocumentElement().getNodeName().equals(rootTagName)) return doc.getDocumentElement().getChildNodes();
          else return null;
        }
        else {
          throw new XIncludeException("Unsupported pointer: " + pointer);
        }
      }
      else {
        throw new XIncludeException("Unsupported XPointer: " + pointerAttr);
      }
    }

    // this method need to remove DocType node if any
    return doc.getChildNodes();
  }


  private static String downloadTextDocument(String url) throws XIncludeException {
    try {
      return new String(StreamUtil.loadFromStream(new URL(url).openStream()));
    }
    catch (IOException e) {
      throw new XIncludeException(e);
    }
  }

}


class XIncludeNodeList implements NodeList {

  private List<Node> data = new ArrayList<Node>();

  // could easily expose more List methods if they seem useful
  public void add(int index, Node node) {
    data.add(index, node);
  }

  public void add(Node node) {
    data.add(node);
  }

  public void add(NodeList nodes) {
    for (int i = 0; i < nodes.getLength(); i++) {
      data.add(nodes.item(i));
    }
  }

  public Node item(int index) {
    return data.get(index);
  }

  // copy DOM JavaDoc
  public int getLength() {
    return data.size();
  }

}


class XIncludeException extends RuntimeException {

  public XIncludeException() {
  }

  public XIncludeException(final String message) {
    super(message);
  }

  public XIncludeException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public XIncludeException(final Throwable cause) {
    super(cause);
  }
}