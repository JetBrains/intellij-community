package org.intellij.plugins.xsltDebugger.rt.engine.local.xalan;

import org.apache.xml.serializer.DOMSerializer;
import org.apache.xml.serializer.NamespaceMappings;
import org.apache.xml.serializer.SerializationHandler;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;
import org.w3c.dom.Node;
import org.xml.sax.*;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.Transformer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Properties;
import java.util.Vector;

class TracingSerializationHandler implements SerializationHandler {
  private final LocalDebugger myDebugger;
  private final SerializationHandler mySerializationHandler;

  public TracingSerializationHandler(LocalDebugger debugger, SerializationHandler handler) {
    myDebugger = debugger;
    mySerializationHandler = handler;

//        final URL location = SerializationHandler.class.getProtectionDomain().getCodeSource().getLocation();
//        System.out.println("location = " + location);
  }

  private static String calcPrefix(String qname) {
    return qname.indexOf(':') == -1 ? "" : qname.split(":")[0];
  }

  public void setContentHandler(ContentHandler ch) {
    mySerializationHandler.setContentHandler(ch);
  }

  public void close() {
    mySerializationHandler.close();
  }

  public void serialize(Node node) throws IOException {
    mySerializationHandler.serialize(node);
  }

  public boolean setEscaping(boolean escape) throws SAXException {
    return mySerializationHandler.setEscaping(escape);
  }

  public void setIndentAmount(int spaces) {
    mySerializationHandler.setIndentAmount(spaces);
  }

  public void setTransformer(Transformer transformer) {
    mySerializationHandler.setTransformer(transformer);
  }

  public Transformer getTransformer() {
    return mySerializationHandler.getTransformer();
  }

  public void setNamespaceMappings(NamespaceMappings mappings) {
    mySerializationHandler.setNamespaceMappings(mappings);
  }

  public void flushPending() throws SAXException {
    mySerializationHandler.flushPending();
  }

  public void setDTDEntityExpansion(boolean expand) {
    mySerializationHandler.setDTDEntityExpansion(expand);
  }

  public void addAttribute(String uri, String localName, String rawName, String type, String value, boolean XSLAttribute)
    throws SAXException {
    myDebugger.getEventQueue().attribute(calcPrefix(rawName), localName, uri, value);
    mySerializationHandler.addAttribute(uri, localName, rawName, type, value, XSLAttribute);
  }

  public void addAttribute(String uri, String localName, String rawName, String type, String value) throws SAXException {
    myDebugger.getEventQueue().attribute("", localName, uri, value);
    mySerializationHandler.addAttribute(uri, localName, rawName, type, value);
  }

  public void addAttributes(Attributes atts) throws SAXException {
    mySerializationHandler.addAttributes(atts);
  }

  public void addAttribute(String qName, String value) {
    mySerializationHandler.addAttribute(qName, value);
  }

  public void characters(String chars) throws SAXException {
    mySerializationHandler.characters(chars);
  }

  public void characters(Node node) throws SAXException {
    mySerializationHandler.characters(node);
  }

  public void endElement(String elemName) throws SAXException {
    mySerializationHandler.endElement(elemName);
  }

  public void startElement(String uri, String localName, String qName) throws SAXException {
    myDebugger.getEventQueue().startElement(calcPrefix(qName), localName, uri);
    mySerializationHandler.startElement(uri, localName, qName);
  }

  public void startElement(String qName) throws SAXException {
    mySerializationHandler.startElement(qName);
  }

  public void namespaceAfterStartElement(String uri, String prefix) throws SAXException {
    mySerializationHandler.namespaceAfterStartElement(uri, prefix);
  }

  public boolean startPrefixMapping(String prefix, String uri, boolean shouldFlush) throws SAXException {
    return mySerializationHandler.startPrefixMapping(prefix, uri, shouldFlush);
  }

  public void entityReference(String entityName) throws SAXException {
    mySerializationHandler.entityReference(entityName);
  }

  public NamespaceMappings getNamespaceMappings() {
    return mySerializationHandler.getNamespaceMappings();
  }

  public String getPrefix(String uri) {
    return mySerializationHandler.getPrefix(uri);
  }

  public String getNamespaceURI(String name, boolean isElement) {
    return mySerializationHandler.getNamespaceURI(name, isElement);
  }

  public String getNamespaceURIFromPrefix(String prefix) {
    return mySerializationHandler.getNamespaceURIFromPrefix(prefix);
  }

  public void setSourceLocator(SourceLocator locator) {
    mySerializationHandler.setSourceLocator(locator);
  }

  public void addUniqueAttribute(String qName, String value, int flags) throws SAXException {
    mySerializationHandler.addUniqueAttribute(qName, value, flags);
  }

  public void addXSLAttribute(String qName, String value, String uri) {
    mySerializationHandler.addXSLAttribute(qName, value, uri);
  }

  public void setDocumentLocator(Locator locator) {
    mySerializationHandler.setDocumentLocator(locator);
  }

  public void startDocument() throws SAXException {
    mySerializationHandler.startDocument();
  }

  public void endDocument() throws SAXException {
    mySerializationHandler.endDocument();
  }

  public void startPrefixMapping(String prefix, String uri) throws SAXException {
    mySerializationHandler.startPrefixMapping(prefix, uri);
  }

  public void endPrefixMapping(String prefix) throws SAXException {
    mySerializationHandler.endPrefixMapping(prefix);
  }

  public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
    mySerializationHandler.startElement(uri, localName, qName, atts);
  }

  public void endElement(String uri, String localName, String qName) throws SAXException {
    mySerializationHandler.endElement(uri, localName, qName);
  }

  public void characters(char[] ch, int start, int length) throws SAXException {
    mySerializationHandler.characters(ch, start, length);
  }

  public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    mySerializationHandler.ignorableWhitespace(ch, start, length);
  }

  public void processingInstruction(String target, String data) throws SAXException {
    mySerializationHandler.processingInstruction(target, data);
  }

  public void skippedEntity(String name) throws SAXException {
    mySerializationHandler.skippedEntity(name);
  }

  public void comment(String comment) throws SAXException {
    mySerializationHandler.comment(comment);
  }

  public void startDTD(String name, String publicId, String systemId) throws SAXException {
    mySerializationHandler.startDTD(name, publicId, systemId);
  }

  public void endDTD() throws SAXException {
    mySerializationHandler.endDTD();
  }

  public void startEntity(String name) throws SAXException {
    mySerializationHandler.startEntity(name);
  }

  public void endEntity(String name) throws SAXException {
    mySerializationHandler.endEntity(name);
  }

  public void startCDATA() throws SAXException {
    mySerializationHandler.startCDATA();
  }

  public void endCDATA() throws SAXException {
    mySerializationHandler.endCDATA();
  }

  public void comment(char[] ch, int start, int length) throws SAXException {
    mySerializationHandler.comment(ch, start, length);
  }

  public String getDoctypePublic() {
    return mySerializationHandler.getDoctypePublic();
  }

  public String getDoctypeSystem() {
    return mySerializationHandler.getDoctypeSystem();
  }

  public String getEncoding() {
    return mySerializationHandler.getEncoding();
  }

  public boolean getIndent() {
    return mySerializationHandler.getIndent();
  }

  public int getIndentAmount() {
    return mySerializationHandler.getIndentAmount();
  }

  public String getMediaType() {
    return mySerializationHandler.getMediaType();
  }

  public boolean getOmitXMLDeclaration() {
    return mySerializationHandler.getOmitXMLDeclaration();
  }

  public String getStandalone() {
    return mySerializationHandler.getStandalone();
  }

  public String getVersion() {
    return mySerializationHandler.getVersion();
  }

  public void setCdataSectionElements(Vector URI_and_localNames) {
    mySerializationHandler.setCdataSectionElements(URI_and_localNames);
  }

  public void setDoctype(String system, String pub) {
    mySerializationHandler.setDoctype(system, pub);
  }

  public void setDoctypePublic(String doctype) {
    mySerializationHandler.setDoctypePublic(doctype);
  }

  public void setDoctypeSystem(String doctype) {
    mySerializationHandler.setDoctypeSystem(doctype);
  }

  public void setEncoding(String encoding) {
    mySerializationHandler.setEncoding(encoding);
  }

  public void setIndent(boolean indent) {
    mySerializationHandler.setIndent(indent);
  }

  public void setMediaType(String mediatype) {
    mySerializationHandler.setMediaType(mediatype);
  }

  public void setOmitXMLDeclaration(boolean b) {
    mySerializationHandler.setOmitXMLDeclaration(b);
  }

  public void setStandalone(String standalone) {
    mySerializationHandler.setStandalone(standalone);
  }

  public void setVersion(String version) {
    mySerializationHandler.setVersion(version);
  }

  public String getOutputProperty(String name) {
    return mySerializationHandler.getOutputProperty(name);
  }

  public String getOutputPropertyDefault(String name) {
    return mySerializationHandler.getOutputPropertyDefault(name);
  }

  public void setOutputProperty(String name, String val) {
    mySerializationHandler.setOutputProperty(name, val);
  }

  public void setOutputPropertyDefault(String name, String val) {
    mySerializationHandler.setOutputPropertyDefault(name, val);
  }

  public void elementDecl(String name, String model) throws SAXException {
    mySerializationHandler.elementDecl(name, model);
  }

  public void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException {
    mySerializationHandler.attributeDecl(eName, aName, type, mode, value);
  }

  public void internalEntityDecl(String name, String value) throws SAXException {
    mySerializationHandler.internalEntityDecl(name, value);
  }

  public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
    mySerializationHandler.externalEntityDecl(name, publicId, systemId);
  }

  public void notationDecl(String name, String publicId, String systemId) throws SAXException {
    mySerializationHandler.notationDecl(name, publicId, systemId);
  }

  public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
    mySerializationHandler.unparsedEntityDecl(name, publicId, systemId, notationName);
  }

  public void warning(SAXParseException exception) throws SAXException {
    mySerializationHandler.warning(exception);
  }

  public void error(SAXParseException exception) throws SAXException {
    mySerializationHandler.error(exception);
  }

  public void fatalError(SAXParseException exception) throws SAXException {
    mySerializationHandler.fatalError(exception);
  }

  public void setOutputStream(OutputStream output) {
    mySerializationHandler.setOutputStream(output);
  }

  public OutputStream getOutputStream() {
    return mySerializationHandler.getOutputStream();
  }

  public void setWriter(Writer writer) {
    mySerializationHandler.setWriter(writer);
  }

  public Writer getWriter() {
    return mySerializationHandler.getWriter();
  }

  public void setOutputFormat(Properties format) {
    mySerializationHandler.setOutputFormat(format);
  }

  public Properties getOutputFormat() {
    return mySerializationHandler.getOutputFormat();
  }

  public ContentHandler asContentHandler() throws IOException {
    return mySerializationHandler.asContentHandler();
  }

  public DOMSerializer asDOMSerializer() throws IOException {
    return mySerializationHandler.asDOMSerializer();
  }

  public boolean reset() {
    return mySerializationHandler.reset();
  }

  public Object asDOM3Serializer() throws IOException {
    return mySerializationHandler.asDOM3Serializer();
  }
}