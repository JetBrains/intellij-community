package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.InternalIterator;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RendererConfiguration implements Cloneable, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.NodeRendererSettings");
  private static final String AUTO_NODE = "node";

  private static final int VERSION = 6;

  private List<AutoRendererNode> myRepresentationNodes = new ArrayList<AutoRendererNode>();
  private final NodeRendererSettings myRendererSettings;

  protected RendererConfiguration(NodeRendererSettings rendererSettings) {
    myRendererSettings = rendererSettings;
  }

  public RendererConfiguration clone() {
    RendererConfiguration result = null;
    try {
      result = (RendererConfiguration)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    result.myRepresentationNodes = new ArrayList<AutoRendererNode>();
    for (Iterator<AutoRendererNode> iterator = myRepresentationNodes.iterator(); iterator.hasNext();) {
      AutoRendererNode autoRendererNode = iterator.next();
      result.addNode(autoRendererNode.clone());
    }

    return result;
  }

  public boolean equals(Object o) {
    if(!(o instanceof RendererConfiguration)) return false;

    return DebuggerUtilsEx.externalizableEqual(this, (RendererConfiguration)o);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    for (Iterator<AutoRendererNode> iterator = myRepresentationNodes.iterator(); iterator.hasNext();) {
      AutoRendererNode autoRendererNode = iterator.next();
      Element nodeElement = new Element(AUTO_NODE);
      autoRendererNode.writeExternal(nodeElement);
      element.addContent(nodeElement);
    }
    element.setAttribute("VERSION", String.valueOf(VERSION));
  }

  public void readExternal(final Element root) {
    String versionAttrib = root.getAttributeValue("VERSION");
    int configurationVersion = -1;
    if (versionAttrib != null) {
      try {
        configurationVersion = Integer.parseInt(versionAttrib);
      }
      catch (NumberFormatException e) {
        configurationVersion = -1;
      }
    }
    if(configurationVersion != VERSION) {
      return;
    }

    List<Element> children = root.getChildren(AUTO_NODE);

    myRepresentationNodes.clear();
    for (Iterator<Element> iterator = children.iterator(); iterator.hasNext();) {
      Element nodeRepresentation = iterator.next();
      try {
        addNode(AutoRendererNode.read(nodeRepresentation, myRendererSettings));
      } catch (Exception e) {
        LOG.debug(e);
      }
    }
  }

  private void addNode(AutoRendererNode nodeRepresentation) {
    LOG.assertTrue(nodeRepresentation != null);
    myRepresentationNodes.add(nodeRepresentation);
  }

  public void addRenderer(NodeRenderer renderer) {
    addNode(new AutoRendererNode(renderer));
  }

  public void removeRenderer(NodeRenderer renderer) {
    myRepresentationNodes.remove(renderer);
  }

  public List<AutoRendererNode> getAutoNodes() {
    List<AutoRendererNode> result = new ArrayList<AutoRendererNode>();

    for (Iterator<AutoRendererNode> iterator = myRepresentationNodes.iterator(); iterator.hasNext();) {
      AutoRendererNode autoRendererNode = iterator.next();
      result.add(autoRendererNode);
    }

    return result;
  }

  public void setAutoNodes(List<AutoRendererNode> nodes) {
    myRepresentationNodes = nodes;
  }

  public void iterateRenderers(InternalIterator<AutoRendererNode> iterator) {
    for (Iterator<AutoRendererNode> it = myRepresentationNodes.iterator(); it.hasNext();) {
      AutoRendererNode autoRendererNode = it.next();
      final boolean shouldContinue = iterator.visit(autoRendererNode);
      if (!shouldContinue) {
        break;
      }
    }
  }

  public int getRendererCount() {
    return myRepresentationNodes.size();
  }
}
