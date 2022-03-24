// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.variablesview.usertyperenderers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.jetbrains.python.debugger.PyUserTypeRenderer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

@State(name = "PyUserTypeRenderersSettings", storages = @Storage("user-variables-view-settings.xml"))
public class PyUserTypeRenderersSettings implements PersistentStateComponent<Element> {

  private static final String LIST_TAG = "LIST";
  private static final String RENDERER_TAG = "RENDERER";
  private static final String VALUE_RENDERER_TAG = "VALUE_RENDERER";
  private static final String CHILDREN_RENDERER_TAG = "CHILDREN_RENDERER";
  private static final String CHILD_TAG = "CHILD";

  private static final String NAME = "name";
  private static final String IS_ENABLED = "isEnabled";
  private static final String IS_DEFAULT = "isDefault";
  private static final String EXPRESSION = "expression";
  private static final String TO_TYPE = "toType";
  private static final String TYPE_CANONICAL_IMPORT_PATH = "typeCanonicalImportPath";
  private static final String TYPE_QUALIFIED_NAME = "typeQualifiedName";
  private static final String TYPE_SOURCE_FILE = "typeSourceFile";
  private static final String APPEND_DEFAULT_CHILDREN = "appendDefaultChildren";

  private final @NotNull ArrayList<@NotNull PyUserNodeRenderer> myRenderers = new ArrayList<>();

  public static PyUserTypeRenderersSettings getInstance() {
    return ApplicationManager.getApplication().getService(PyUserTypeRenderersSettings.class);
  }

  @Override
  public synchronized @Nullable Element getState() {
    final Element listElement = new Element(LIST_TAG);
    for (PyUserNodeRenderer renderer : myRenderers) {
      addRendererToElement(renderer, listElement);
    }
    return listElement;
  }

  @Override
  public synchronized void loadState(final @NotNull Element state) {
    for (Element rendererElement : state.getChildren(RENDERER_TAG)) {
      myRenderers.add(loadRenderer(rendererElement));
    }
  }

  public synchronized @NotNull ArrayList<@NotNull PyUserNodeRenderer> getRenderers() {
    return myRenderers;
  }

  public synchronized @NotNull ArrayList<@NotNull PyUserTypeRenderer> getApplicableRenderers() {
    HashSet<String> foundTypes = new HashSet<>();
    ArrayList<PyUserTypeRenderer> filtered = new ArrayList<>();
    for (PyUserNodeRenderer renderer : myRenderers) {
      if (renderer.isApplicable() && !foundTypes.contains(renderer.getToType())) {
        filtered.add(renderer.convertRenderer());
        foundTypes.add(renderer.getToType());
      }
    }
    return filtered;
  }

  public synchronized void setRenderers(@NotNull Collection<@NotNull PyUserNodeRenderer> newRenderers) {
    myRenderers.clear();
    myRenderers.addAll(newRenderers);
  }

  private static void addRendererToElement(final @NotNull PyUserNodeRenderer renderer, Element parent) {
    final Element rendererElement = new Element(RENDERER_TAG);
    JDOMExternalizerUtil.writeField(rendererElement, NAME, renderer.getName());
    JDOMExternalizerUtil.writeField(rendererElement, IS_ENABLED, String.valueOf(renderer.isEnabled()));
    JDOMExternalizerUtil.writeField(rendererElement, TO_TYPE, renderer.getToType());
    JDOMExternalizerUtil.writeField(rendererElement, TYPE_CANONICAL_IMPORT_PATH, renderer.getTypeCanonicalImportPath());
    JDOMExternalizerUtil.writeField(rendererElement, TYPE_QUALIFIED_NAME, renderer.getTypeQualifiedName());
    JDOMExternalizerUtil.writeField(rendererElement, TYPE_SOURCE_FILE, renderer.getTypeSourceFile());

    final Element valueRendererElement = new Element(VALUE_RENDERER_TAG);
    final PyUserNodeRenderer.PyNodeValueRenderer valueRenderer = renderer.getValueRenderer();
    JDOMExternalizerUtil.writeField(valueRendererElement, IS_DEFAULT, String.valueOf(valueRenderer.isDefault()));
    JDOMExternalizerUtil.writeField(valueRendererElement, EXPRESSION, valueRenderer.getExpression());
    rendererElement.addContent(valueRendererElement);

    final Element childrenRendererElement = new Element(CHILDREN_RENDERER_TAG);
    final PyUserNodeRenderer.PyNodeChildrenRenderer childrenRenderer = renderer.getChildrenRenderer();
    JDOMExternalizerUtil.writeField(childrenRendererElement, IS_DEFAULT, String.valueOf(childrenRenderer.isDefault()));
    JDOMExternalizerUtil.writeField(
      childrenRendererElement,
      APPEND_DEFAULT_CHILDREN,
      String.valueOf(childrenRenderer.getAppendDefaultChildren())
    );
    for (PyUserNodeRenderer.ChildInfo child : childrenRenderer.getChildren()) {
      final Element childElement = new Element(CHILD_TAG);
      JDOMExternalizerUtil.writeField(childElement, EXPRESSION, child.getExpression());
      childrenRendererElement.addContent(childElement);
    }
    rendererElement.addContent(childrenRendererElement);

    parent.addContent(rendererElement);
  }

  private static @NotNull String toString(@Nullable String s) {
    return s == null ? "" : s;
  }

  private static boolean toBoolean(@Nullable String s) {
    return Boolean.parseBoolean(s);
  }

  private static @NotNull PyUserNodeRenderer loadRenderer(final @NotNull Element element) {
    PyUserNodeRenderer renderer = new PyUserNodeRenderer(false, null);
    String name = toString(JDOMExternalizerUtil.readField(element, NAME));
    boolean isEnabled = toBoolean(JDOMExternalizerUtil.readField(element, IS_ENABLED));
    @NotNull String toType = toString(JDOMExternalizerUtil.readField(element, TO_TYPE));
    @NotNull String typeCanonicalImportPath = toString(JDOMExternalizerUtil.readField(element, TYPE_CANONICAL_IMPORT_PATH));
    @NotNull String typeQualifiedName = toString(JDOMExternalizerUtil.readField(element, TYPE_QUALIFIED_NAME));
    @NotNull String typeSourceFile = toString(JDOMExternalizerUtil.readField(element, TYPE_SOURCE_FILE));
    renderer.setName(name);
    renderer.setEnabled(isEnabled);
    renderer.setToType(toType);
    renderer.setTypeCanonicalImportPath(typeCanonicalImportPath);
    renderer.setTypeQualifiedName(typeQualifiedName);
    renderer.setTypeSourceFile(typeSourceFile);

    @Nullable Element valueRendererElement = element.getChild(VALUE_RENDERER_TAG);
    if (valueRendererElement != null) {
      boolean isDefault = toBoolean(JDOMExternalizerUtil.readField(valueRendererElement, IS_DEFAULT));
      @NotNull String expression = toString(JDOMExternalizerUtil.readField(valueRendererElement, EXPRESSION));
      renderer.getValueRenderer().setDefault(isDefault);
      renderer.getValueRenderer().setExpression(expression);
    }

    @Nullable Element childrenRendererElement = element.getChild(CHILDREN_RENDERER_TAG);
    if (childrenRendererElement != null) {
      boolean isDefault = toBoolean(JDOMExternalizerUtil.readField(childrenRendererElement, IS_DEFAULT));
      boolean appendDefaultChildren = toBoolean(JDOMExternalizerUtil.readField(childrenRendererElement, APPEND_DEFAULT_CHILDREN));
      ArrayList<PyUserNodeRenderer.ChildInfo> list = new ArrayList<>();
      for (Element childElement : childrenRendererElement.getChildren(CHILD_TAG)) {
        @NotNull String expression = toString(JDOMExternalizerUtil.readField(childElement, EXPRESSION));
        list.add(new PyUserNodeRenderer.ChildInfo(expression));
      }
      renderer.getChildrenRenderer().setDefault(isDefault);
      renderer.getChildrenRenderer().setAppendDefaultChildren(appendDefaultChildren);
      renderer.getChildrenRenderer().setChildren(list);
    }

    return renderer;
  }
}