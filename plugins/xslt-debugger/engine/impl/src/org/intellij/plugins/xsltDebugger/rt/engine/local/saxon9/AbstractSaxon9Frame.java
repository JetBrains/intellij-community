package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.local.AbstractFrame;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;

class AbstractSaxon9Frame<F extends Debugger.Frame, N extends SourceLocator> extends AbstractFrame<F> {
  protected final N myElement;

  protected AbstractSaxon9Frame(F prev, N element) {
    super(prev);
    myElement = element;
  }

  public int getLineNumber() {
    return myElement.getLineNumber();
  }

  public String getURI() {
    final String uri = myElement.getSystemId();
    return uri != null ? uri.replaceAll(" ", "%20") : null;
  }
}