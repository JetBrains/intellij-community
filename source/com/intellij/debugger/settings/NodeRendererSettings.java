package com.intellij.debugger.settings;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.InternalIterator;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

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

  // base renderers
  private final PrimitiveRenderer myPrimitiveRenderer = new PrimitiveRenderer();
  private final ArrayRenderer myArrayRenderer = new ArrayRenderer();
  private final ClassRenderer myClassRenderer = new ClassRenderer();
  private final HexRenderer myHexRenderer = new HexRenderer();
  private final ToStringRenderer myToStringRenderer = new ToStringRenderer();
  // alternate collections
  private final NodeRenderer[] myAlternateCollectionRenderers = new NodeRenderer[]{
      createCompoundReferenceRenderer(
        "Map", "java.util.Map",
        createLabelRenderer("size = ", "size()", null),
        createExpressionChildrenRenderer("entrySet().toArray()", "!isEmpty()")
      ),
      createCompoundReferenceRenderer(
        "Map.Entry", "java.util.Map$Entry",
        createLabelRenderer(null, "\" \" + getKey() + \" -> \" + getValue()", null),
        createEnumerationChildrenRenderer(new String[][]{{"key", "getKey()"}, {"value", "getValue()"}})
      ),
      createCompoundReferenceRenderer(
        "Collection", "java.util.Collection",
        createLabelRenderer("size = ", "size()", null),
        createExpressionChildrenRenderer("toArray()", "!isEmpty()")
      )
    };

  public NodeRendererSettings() {
    // default configuration
    myHexRenderer.setEnabled(false);
    myToStringRenderer.setEnabled(true);
    setAlternateCollectionViewsEnabled(true);
  }

  public static NodeRendererSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(NodeRendererSettings.class);
  }

  public void setAlternateCollectionViewsEnabled(boolean enabled) {
    for (int idx = 0; idx < myAlternateCollectionRenderers.length; idx++) {
      myAlternateCollectionRenderers[idx].setEnabled(enabled);
    }
  }

  public boolean areAlternateCollectionViewsEnabled() {
    return myAlternateCollectionRenderers[0].isEnabled();
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

  public void iterateUserRenderers(InternalIterator<AutoRendererNode> iterator) {
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

  public ToStringRenderer getToStringRenderer() {
    return myToStringRenderer;
  }

  public void fireRenderersChanged() {
    myDispatcher.getMulticaster().renderersChanged();
  }

  public List<NodeRenderer> getAllRenderers() {
    // the order is important as the renderers are applied according to it
    final List<NodeRenderer> allRenderers = new ArrayList<NodeRenderer>();
    allRenderers.add(myHexRenderer);
    allRenderers.add(myPrimitiveRenderer);
    myRendererConfiguration.iterateRenderers(new InternalIterator<AutoRendererNode>() {
      public boolean visit(final AutoRendererNode element) {
        allRenderers.add(element.getRenderer());
        return true;
      }
    });
    for (int idx = 0; idx < myAlternateCollectionRenderers.length; idx++) {
      allRenderers.add(myAlternateCollectionRenderers[idx]);
    }
    allRenderers.add(myToStringRenderer);
    allRenderers.add(myArrayRenderer);
    allRenderers.add(myClassRenderer);
    return allRenderers;
  }

  public boolean isBase(final Renderer renderer) {
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
      return myToStringRenderer;
    }
    else if(rendererId.equals(CompoundNodeRenderer.UNIQUE_ID) || rendererId.equals(REFERENCE_RENDERER)) {
      return createCompoundReferenceRenderer("unnamed", "java.lang.Object", null, null);
    }
    return null;
  }

  private CompoundReferenceRenderer createCompoundReferenceRenderer(
    final String rendererName, final String className, final LabelRenderer labelRenderer, final ChildrenRenderer childrenRenderer
    ) {
    CompoundReferenceRenderer renderer = new CompoundReferenceRenderer(this, rendererName, labelRenderer, childrenRenderer);
    renderer.setClassName(className);
    return renderer;
  }

  private ExpressionChildrenRenderer createExpressionChildrenRenderer(String expressionText, String childrenExpandableText) {
    final ExpressionChildrenRenderer childrenRenderer = new ExpressionChildrenRenderer();
    childrenRenderer.setChildrenExpression(new TextWithImportsImpl(TextWithImportsImpl.EXPRESSION_FACTORY, expressionText));
    if (childrenExpandableText != null) {
      childrenRenderer.setChildrenExpandable(new TextWithImportsImpl(TextWithImportsImpl.EXPRESSION_FACTORY, childrenExpandableText));
    }
    return childrenRenderer;
  }

  private EnumerationChildrenRenderer createEnumerationChildrenRenderer(String[][] expressions) {
    final EnumerationChildrenRenderer childrenRenderer = new EnumerationChildrenRenderer();
    if (expressions != null && expressions.length > 0) {
      final ArrayList<Pair<String, TextWithImports>> childrenList = new ArrayList<Pair<String, TextWithImports>>(expressions.length);
      for (int idx = 0; idx < expressions.length; idx++) {
        final String[] expression = expressions[idx];
        childrenList.add(new Pair<String, TextWithImports>(expression[0], new TextWithImportsImpl(TextWithImportsImpl.EXPRESSION_FACTORY, expression[1])));
      }
      childrenRenderer.setChildren(childrenList);
    }
    return childrenRenderer;
  }

  private LabelRenderer createLabelRenderer(final String prefix, final String expressionText, final String postfix) {
    final LabelRenderer labelRenderer = new LabelRenderer() {
      public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) throws EvaluateException {
        final String evaluated = super.calcLabel(descriptor, evaluationContext, labelListener);
        if (prefix == null && postfix == null) {
          return evaluated;
        }
        if (prefix != null && postfix != null) {
          return prefix + evaluated + postfix;
        }
        if (prefix != null) {
          return prefix + evaluated;
        }
        return evaluated + postfix;
      }
    };
    labelRenderer.setLabelExpression(new TextWithImportsImpl(TextWithImportsImpl.EXPRESSION_FACTORY, expressionText));
    return labelRenderer;
  }

}
