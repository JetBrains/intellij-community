package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.serialize.Emitter;
import net.sf.saxon.trans.XPathException;
import org.intellij.plugins.xsltDebugger.rt.engine.local.OutputEventQueueImpl;

import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Properties;

final class TracingOutputter extends Emitter {
  private final OutputEventQueueImpl myEventQueue;
  private final Emitter myEmitter;

  public TracingOutputter(OutputEventQueueImpl queue, Emitter emitter) {
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

  public void open() throws XPathException {
    myEmitter.open();
  }

  public void startDocument(int properties) throws XPathException {
    myEmitter.startDocument(properties);
  }

  public void endDocument() throws XPathException {
    myEmitter.endDocument();
  }

  public void namespace(int namespaceCode, int properties) throws XPathException {
    myEmitter.namespace(namespaceCode, properties);
  }

  @Override
  public void close() throws XPathException {
    myEmitter.close();
  }

  public void startElement(int nameCode, int typeCode, int locationId, int properties) throws XPathException {
    if (myEventQueue.isEnabled()) {
      final String localName = namePool.getLocalName(nameCode);
      final String prefix = namePool.getPrefix(nameCode);
      myEventQueue.startElement(prefix, localName, namePool.getURI(nameCode));
    }
    myEmitter.startElement(nameCode, typeCode, locationId, properties);
  }


  public void attribute(int nameCode, int typeCode, CharSequence value, int locationId, int properties) throws XPathException {
    if (myEventQueue.isEnabled()) {
      final String localName = namePool.getLocalName(nameCode);
      final String prefix = namePool.getPrefix(nameCode);
      myEventQueue.attribute(prefix, localName, namePool.getURI(nameCode), value.toString());
    }
    myEmitter.attribute(nameCode, typeCode, value, locationId, properties);
  }

  public void startContent() throws XPathException {
  }

  public void endElement() throws XPathException {
    myEventQueue.endElement();
    myEmitter.endElement();
  }

  public void characters(CharSequence chars, int locationId, int properties) throws XPathException {
    myEventQueue.characters(chars.toString());
    myEmitter.characters(chars, locationId, properties);
  }

  public void processingInstruction(String name, CharSequence data, int locationId, int properties) throws XPathException {
    myEventQueue.pi(name, data.toString());
    myEmitter.processingInstruction(name, data, locationId, properties);
  }

  public void comment(CharSequence content, int locationId, int properties) throws XPathException {
    myEventQueue.comment(content.toString());
    myEmitter.comment(content, locationId, properties);
  }

  public boolean usesTypeAnnotations() {
    return false;
  }
}
