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

  TracingSerializationHandler(LocalDebugger debugger, SerializationHandler handler) {
    myDebugger = debugger;
    mySerializationHandler = handler;

//        final URL location = SerializationHandler.class.getProtectionDomain().getCodeSource().getLocation();
//        System.out.println("location = " + location);
  }

  private static String calcPrefix(String qname) {
    return qname.indexOf(':') == -1 ? "" : qname.split(":")[0];
  }

  @Override
  public void setContentHandler(ContentHandler ch) {
    mySerializationHandler.setContentHandler(ch);
  }

  @Override
  public void close() {
    mySerializationHandler.close();
  }

  @Override
  public void serialize(Node node) throws IOException {
    mySerializationHandler.serialize(node);
  }

  @Override
  public boolean setEscaping(boolean escape) throws SAXException {
    return mySerializationHandler.setEscaping(escape);
  }

  @Override
  public void setIndentAmount(int spaces) {
    mySerializationHandler.setIndentAmount(spaces);
  }

  @Override
  public void setTransformer(Transformer transformer) {
    mySerializationHandler.setTransformer(transformer);
  }

  @Override
  public Transformer getTransformer() {
    return mySerializationHandler.getTransformer();
  }

  @Override
  public void setNamespaceMappings(NamespaceMappings mappings) {
    mySerializationHandler.setNamespaceMappings(mappings);
  }

  @Override
  public void flushPending() throws SAXException {
    mySerializationHandler.flushPending();
  }

  @Override
  public void setDTDEntityExpansion(boolean expand) {
    mySerializationHandler.setDTDEntityExpansion(expand);
  }

  @Override
  public void addAttribute(String uri, String localName, String rawName, String type, String value, boolean XSLAttribute)
    throws SAXException {
    myDebugger.getEventQueue().attribute(calcPrefix(rawName), localName, uri, value);
    mySerializationHandler.addAttribute(uri, localName, rawName, type, value, XSLAttribute);
  }

  @Override
  public void addAttribute(String uri, String localName, String rawName, String type, String value) throws SAXException {
    myDebugger.getEventQueue().attribute("", localName, uri, value);
    mySerializationHandler.addAttribute(uri, localName, rawName, type, value);
  }

  @Override
  public void addAttributes(Attributes atts) throws SAXException {
    mySerializationHandler.addAttributes(atts);
  }

  @Override
  public void addAttribute(String qName, String value) {
    mySerializationHandler.addAttribute(qName, value);
  }

  @Override
  public void characters(String chars) throws SAXException {
    mySerializationHandler.characters(chars);
  }

  @Override
  public void characters(Node node) throws SAXException {
    mySerializationHandler.characters(node);
  }

  @Override
  public void endElement(String elemName) throws SAXException {
    mySerializationHandler.endElement(elemName);
  }

  @Override
  public void startElement(String uri, String localName, String qName) throws SAXException {
    myDebugger.getEventQueue().startElement(calcPrefix(qName), localName, uri);
    mySerializationHandler.startElement(uri, localName, qName);
  }

  @Override
  public void startElement(String qName) throws SAXException {
    mySerializationHandler.startElement(qName);
  }

  @Override
  public void namespaceAfterStartElement(String uri, String prefix) throws SAXException {
    mySerializationHandler.namespaceAfterStartElement(uri, prefix);
  }

  @Override
  public boolean startPrefixMapping(String prefix, String uri, boolean shouldFlush) throws SAXException {
    return mySerializationHandler.startPrefixMapping(prefix, uri, shouldFlush);
  }

  @Override
  public void entityReference(String entityName) throws SAXException {
    mySerializationHandler.entityReference(entityName);
  }

  @Override
  public NamespaceMappings getNamespaceMappings() {
    return mySerializationHandler.getNamespaceMappings();
  }

  @Override
  public String getPrefix(String uri) {
    return mySerializationHandler.getPrefix(uri);
  }

  @Override
  public String getNamespaceURI(String name, boolean isElement) {
    return mySerializationHandler.getNamespaceURI(name, isElement);
  }

  @Override
  public String getNamespaceURIFromPrefix(String prefix) {
    return mySerializationHandler.getNamespaceURIFromPrefix(prefix);
  }

  @Override
  public void setSourceLocator(SourceLocator locator) {
    mySerializationHandler.setSourceLocator(locator);
  }

  @Override
  public void addUniqueAttribute(String qName, String value, int flags) throws SAXException {
    mySerializationHandler.addUniqueAttribute(qName, value, flags);
  }

  @Override
  public void addXSLAttribute(String qName, String value, String uri) {
    mySerializationHandler.addXSLAttribute(qName, value, uri);
  }

  @Override
  public void setDocumentLocator(Locator locator) {
    mySerializationHandler.setDocumentLocator(locator);
  }

  @Override
  public void startDocument() throws SAXException {
    mySerializationHandler.startDocument();
  }

  @Override
  public void endDocument() throws SAXException {
    mySerializationHandler.endDocument();
  }

  @Override
  public void startPrefixMapping(String prefix, String uri) throws SAXException {
    mySerializationHandler.startPrefixMapping(prefix, uri);
  }

  @Override
  public void endPrefixMapping(String prefix) throws SAXException {
    mySerializationHandler.endPrefixMapping(prefix);
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
    mySerializationHandler.startElement(uri, localName, qName, atts);
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    mySerializationHandler.endElement(uri, localName, qName);
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    mySerializationHandler.characters(ch, start, length);
  }

  @Override
  public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    mySerializationHandler.ignorableWhitespace(ch, start, length);
  }

  @Override
  public void processingInstruction(String target, String data) throws SAXException {
    mySerializationHandler.processingInstruction(target, data);
  }

  @Override
  public void skippedEntity(String name) throws SAXException {
    mySerializationHandler.skippedEntity(name);
  }

  @Override
  public void comment(String comment) throws SAXException {
    mySerializationHandler.comment(comment);
  }

  @Override
  public void startDTD(String name, String publicId, String systemId) throws SAXException {
    mySerializationHandler.startDTD(name, publicId, systemId);
  }

  @Override
  public void endDTD() throws SAXException {
    mySerializationHandler.endDTD();
  }

  @Override
  public void startEntity(String name) throws SAXException {
    mySerializationHandler.startEntity(name);
  }

  @Override
  public void endEntity(String name) throws SAXException {
    mySerializationHandler.endEntity(name);
  }

  @Override
  public void startCDATA() throws SAXException {
    mySerializationHandler.startCDATA();
  }

  @Override
  public void endCDATA() throws SAXException {
    mySerializationHandler.endCDATA();
  }

  @Override
  public void comment(char[] ch, int start, int length) throws SAXException {
    mySerializationHandler.comment(ch, start, length);
  }

  @Override
  public String getDoctypePublic() {
    return mySerializationHandler.getDoctypePublic();
  }

  @Override
  public String getDoctypeSystem() {
    return mySerializationHandler.getDoctypeSystem();
  }

  @Override
  public String getEncoding() {
    return mySerializationHandler.getEncoding();
  }

  @Override
  public boolean getIndent() {
    return mySerializationHandler.getIndent();
  }

  @Override
  public int getIndentAmount() {
    return mySerializationHandler.getIndentAmount();
  }

  @Override
  public String getMediaType() {
    return mySerializationHandler.getMediaType();
  }

  @Override
  public boolean getOmitXMLDeclaration() {
    return mySerializationHandler.getOmitXMLDeclaration();
  }

  @Override
  public String getStandalone() {
    return mySerializationHandler.getStandalone();
  }

  @Override
  public String getVersion() {
    return mySerializationHandler.getVersion();
  }

  @Override
  public void setCdataSectionElements(Vector URI_and_localNames) {
    mySerializationHandler.setCdataSectionElements(URI_and_localNames);
  }

  @Override
  public void setDoctype(String system, String pub) {
    mySerializationHandler.setDoctype(system, pub);
  }

  @Override
  public void setDoctypePublic(String doctype) {
    mySerializationHandler.setDoctypePublic(doctype);
  }

  @Override
  public void setDoctypeSystem(String doctype) {
    mySerializationHandler.setDoctypeSystem(doctype);
  }

  @Override
  public void setEncoding(String encoding) {
    mySerializationHandler.setEncoding(encoding);
  }

  @Override
  public void setIndent(boolean indent) {
    mySerializationHandler.setIndent(indent);
  }

  @Override
  public void setMediaType(String mediatype) {
    mySerializationHandler.setMediaType(mediatype);
  }

  @Override
  public void setOmitXMLDeclaration(boolean b) {
    mySerializationHandler.setOmitXMLDeclaration(b);
  }

  @Override
  public void setStandalone(String standalone) {
    mySerializationHandler.setStandalone(standalone);
  }

  @Override
  public void setVersion(String version) {
    mySerializationHandler.setVersion(version);
  }

  @Override
  public String getOutputProperty(String name) {
    return mySerializationHandler.getOutputProperty(name);
  }

  @Override
  public String getOutputPropertyDefault(String name) {
    return mySerializationHandler.getOutputPropertyDefault(name);
  }

  @Override
  public void setOutputProperty(String name, String val) {
    mySerializationHandler.setOutputProperty(name, val);
  }

  @Override
  public void setOutputPropertyDefault(String name, String val) {
    mySerializationHandler.setOutputPropertyDefault(name, val);
  }

  @Override
  public void elementDecl(String name, String model) throws SAXException {
    mySerializationHandler.elementDecl(name, model);
  }

  @Override
  public void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException {
    mySerializationHandler.attributeDecl(eName, aName, type, mode, value);
  }

  @Override
  public void internalEntityDecl(String name, String value) throws SAXException {
    mySerializationHandler.internalEntityDecl(name, value);
  }

  @Override
  public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
    mySerializationHandler.externalEntityDecl(name, publicId, systemId);
  }

  @Override
  public void notationDecl(String name, String publicId, String systemId) throws SAXException {
    mySerializationHandler.notationDecl(name, publicId, systemId);
  }

  @Override
  public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
    mySerializationHandler.unparsedEntityDecl(name, publicId, systemId, notationName);
  }

  @Override
  public void warning(SAXParseException exception) throws SAXException {
    mySerializationHandler.warning(exception);
  }

  @Override
  public void error(SAXParseException exception) throws SAXException {
    mySerializationHandler.error(exception);
  }

  @Override
  public void fatalError(SAXParseException exception) throws SAXException {
    mySerializationHandler.fatalError(exception);
  }

  @Override
  public void setOutputStream(OutputStream output) {
    mySerializationHandler.setOutputStream(output);
  }

  @Override
  public OutputStream getOutputStream() {
    return mySerializationHandler.getOutputStream();
  }

  @Override
  public void setWriter(Writer writer) {
    mySerializationHandler.setWriter(writer);
  }

  @Override
  public Writer getWriter() {
    return mySerializationHandler.getWriter();
  }

  @Override
  public void setOutputFormat(Properties format) {
    mySerializationHandler.setOutputFormat(format);
  }

  @Override
  public Properties getOutputFormat() {
    return mySerializationHandler.getOutputFormat();
  }

  @Override
  public ContentHandler asContentHandler() throws IOException {
    return mySerializationHandler.asContentHandler();
  }

  @Override
  public DOMSerializer asDOMSerializer() throws IOException {
    return mySerializationHandler.asDOMSerializer();
  }

  @Override
  public boolean reset() {
    return mySerializationHandler.reset();
  }

  @Override
  public Object asDOM3Serializer() throws IOException {
    return mySerializationHandler.asDOM3Serializer();
  }
}