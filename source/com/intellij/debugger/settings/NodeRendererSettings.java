package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.InternalIterator;
import org.jdom.Element;

import java.util.List;
import java.util.ArrayList;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 8:00:25 PM
 */
public class NodeRendererSettings implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.settings.NodeRendererSettings");
  private static final String REFERENCE_RENDERER = "Reference renderer";
  public static final String RENDERER_TAG = "Renderer";
  private static final String RENDERER_ID = "ID";

  private final EventDispatcher<NodeRendererSettingsListener> myDispatcher = EventDispatcher.create(NodeRendererSettingsListener.class);
  private RendererConfiguration myRendererConfiguration = new RendererConfiguration(this);

  // default singleton renderers
  private final PrimitiveRenderer myPrimitiveRenderer = new PrimitiveRenderer();
  private final ArrayRenderer myArrayRenderer = new ArrayRenderer();
  private final ClassRenderer myClassRenderer = new ClassRenderer();
  private final HexRenderer myHexRenderer = new HexRenderer();


  public static NodeRendererSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(NodeRendererSettings.class);
  }

  public String getComponentName() {
    return "NodeRendererSettings";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getExternalFileName() {
    return "debugger.renderers";
  }

  public boolean equals(Object o) {
    if(!(o instanceof NodeRendererSettings)) return false;

    return DebuggerUtilsEx.externalizableEqual(this, (NodeRendererSettings)o);
  }

  public void addListener(NodeRendererSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(NodeRendererSettingsListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    myRendererConfiguration.writeExternal(element);
  }

  public void readExternal(final Element root) {
    myRendererConfiguration.readExternal(root);
    myDispatcher.getMulticaster().renderersChanged();
  }

  public RendererConfiguration getRendererConfiguration() {
    return myRendererConfiguration;
  }

  public void setRendererConfiguration(final RendererConfiguration rendererConfiguration) {
    LOG.assertTrue(rendererConfiguration != null);
    RendererConfiguration oldConfig = myRendererConfiguration;
    myRendererConfiguration = rendererConfiguration;
    if (oldConfig == null || !oldConfig.equals(rendererConfiguration)) {
      fireRenderersChanged();
    }
  }

  public void iterateRenderers(InternalIterator<AutoRendererNode> iterator) {
    myRendererConfiguration.iterateRenderers(iterator);
  }

  public PrimitiveRenderer getPrimitiveRenderer() {
    return myPrimitiveRenderer;
  }

  public ArrayRenderer getArrayRenderer() {
    return myArrayRenderer;
  }

  public ClassRenderer getClassRenderer() {
    return myClassRenderer;
  }

  public HexRenderer getHexRenderer() {
    return myHexRenderer;
  }

  public void fireRenderersChanged() {
    myDispatcher.getMulticaster().renderersChanged();
  }

  public List<NodeRenderer> getAllRenderers() {
    final List<NodeRenderer> allRenderers = new ArrayList<NodeRenderer>();
    myRendererConfiguration.iterateRenderers(new InternalIterator<AutoRendererNode>() {
      public boolean visit(final AutoRendererNode element) {
        allRenderers.add(element.getRenderer());
        return true;
      }
    });
    allRenderers.add(myArrayRenderer);
    allRenderers.add(myClassRenderer);
    allRenderers.add(myPrimitiveRenderer);
    allRenderers.add(myHexRenderer);
    return allRenderers;
  }

  public boolean isDefault(final Renderer renderer) {
    return renderer == myPrimitiveRenderer || renderer == myArrayRenderer || renderer == myClassRenderer;
  }

  public Renderer readRenderer(Element root) throws InvalidDataException {
    if (root == null) {
      return null;
    }

    if (!RENDERER_TAG.equals(root.getName())) {
      throw new InvalidDataException("Cannot read renderer - tag name is not '" + RENDERER_TAG + "'");
    }

    final String rendererId = root.getAttributeValue(RENDERER_ID);
    if(rendererId == null) {
      throw new InvalidDataException("unknown renderer ID: " + rendererId);
    }

    final Renderer renderer = createRenderer(rendererId);
    if(renderer == null) {
      throw new InvalidDataException("unknown renderer ID: " + rendererId);
    }

    renderer.readExternal(root);

    return renderer;
  }

  public Element writeRenderer(Renderer renderer) throws WriteExternalException {
    Element root = new Element(RENDERER_TAG);
    if(renderer != null) {
      root.setAttribute(RENDERER_ID  , renderer.getUniqueId());
      renderer.writeExternal(root);
    }
    return root;
  }

  public Renderer createRenderer(final String rendererId) {
    if (ClassRenderer.UNIQUE_ID.equals(rendererId)) {
      return myClassRenderer;
    }
    else if (ArrayRenderer.UNIQUE_ID.equals(rendererId)) {
      return myArrayRenderer;
    }
    else if (PrimitiveRenderer.UNIQUE_ID.equals(rendererId)) {
      return myPrimitiveRenderer;
    }
    else if(HexRenderer.UNIQUE_ID.equals(rendererId)) {
      return myHexRenderer;
    }
    else if(rendererId.equals(ExpressionChildrenRenderer.UNIQUE_ID)) {
      return new ExpressionChildrenRenderer();
    }
    else if(rendererId.equals(LabelRenderer.UNIQUE_ID)) {
      return new LabelRenderer();
    }
    else if(rendererId.equals(EnumerationChildrenRenderer.UNIQUE_ID)) {
      return new EnumerationChildrenRenderer();
    }
    else if(rendererId.equals(ToStringRenderer.UNIQUE_ID)) {
      return new ToStringRenderer();
    }
    else if(rendererId.equals(CompoundNodeRenderer.UNIQUE_ID) || rendererId.equals(REFERENCE_RENDERER)) {
      return new CompoundReferenceRenderer(this, "unnamed", null, null);
    }
    return null;
  }
}
