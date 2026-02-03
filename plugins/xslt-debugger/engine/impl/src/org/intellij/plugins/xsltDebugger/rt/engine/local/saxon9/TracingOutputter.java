package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.Emitter;
import net.sf.saxon.serialize.XMLEmitter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import org.intellij.plugins.xsltDebugger.rt.engine.local.OutputEventQueueImpl;

import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Properties;

final class TracingOutputter extends XMLEmitter {
  private final OutputEventQueueImpl myEventQueue;
  private final Emitter myEmitter;

  TracingOutputter(OutputEventQueueImpl queue, Emitter emitter) {
    myEmitter = emitter;
    myEventQueue = queue;
  }

  @Override
  public void setPipelineConfiguration(PipelineConfiguration pipe) {
    super.setPipelineConfiguration(pipe);
    myEmitter.setPipelineConfiguration(pipe);
  }

  @Override
  public void setSystemId(String systemId) {
    myEmitter.setSystemId(systemId);
  }

  @Override
  public void setOutputProperties(Properties details) throws XPathException {
    myEmitter.setOutputProperties(details);
  }

  @Override
  public void setStreamResult(StreamResult result) throws XPathException {
    myEmitter.setStreamResult(result);
  }

  @Override
  public void setWriter(Writer writer) throws XPathException {
    myEmitter.setWriter(writer);
  }

  @Override
  public void setOutputStream(OutputStream stream) throws XPathException {
    myEmitter.setOutputStream(stream);
  }

  @Override
  public void setUnparsedEntity(String name, String uri, String publicId) throws XPathException {
    myEmitter.setUnparsedEntity(name, uri, publicId);
  }

  @Override
  public void open() throws XPathException {
    myEmitter.open();
  }

  @Override
  public void startDocument(int properties) throws XPathException {
    myEmitter.startDocument(properties);
  }

  @Override
  public void endDocument() throws XPathException {
    myEmitter.endDocument();
  }

  @Override
  public void namespace(NamespaceBindingSet namespaceCode, int properties) throws XPathException {
    myEmitter.namespace(namespaceCode, properties);
  }

  @Override
  public void close() throws XPathException {
    myEmitter.close();
  }

  @Override
  public void startElement(NodeName nodeName, SchemaType schemaType, Location location, int properties) throws XPathException {
    if (myEventQueue.isEnabled()) {
      myEventQueue.startElement(nodeName.getPrefix(), nodeName.getLocalPart(), nodeName.getURI());
    }
    myEmitter.startElement(nodeName, schemaType, location, properties);
  }


  @Override
  public void attribute(NodeName nodeName, SimpleType type, CharSequence value, Location location, int properties) throws XPathException {
    if (myEventQueue.isEnabled()) {
      myEventQueue.attribute(nodeName.getPrefix(), nodeName.getLocalPart(), nodeName.getURI(), value.toString());
    }
    myEmitter.attribute(nodeName, type, value, location, properties);
  }

  @Override
  public void startContent() {
  }

  @Override
  public void endElement() throws XPathException {
    myEventQueue.endElement();
    myEmitter.endElement();
  }

  @Override
  public void characters(CharSequence chars, Location location, int properties) throws XPathException {
    myEventQueue.characters(chars.toString());
    myEmitter.characters(chars, location, properties);
  }

  @Override
  public void processingInstruction(String name, CharSequence data, Location location, int properties) throws XPathException {
    myEventQueue.pi(name, data.toString());
    myEmitter.processingInstruction(name, data, location, properties);
  }

  @Override
  public void comment(CharSequence content, Location location, int properties) throws XPathException {
    myEventQueue.comment(content.toString());
    myEmitter.comment(content, location, properties);
  }

  @Override
  public boolean usesTypeAnnotations() {
    return false;
  }
}
