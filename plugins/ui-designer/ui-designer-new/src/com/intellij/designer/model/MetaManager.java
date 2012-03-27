/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.model;

import com.intellij.designer.palette.Group;
import com.intellij.designer.palette.Item;
import com.intellij.ide.palette.PaletteGroup;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeSupport;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public abstract class MetaManager {
  private static final String META = "meta";
  private static final String PALETTE = "palette";
  private static final String GROUP = "group";
  private static final String NAME = "name";
  private static final String ITEM = "item";
  private static final String TAG = "tag";

  private static final Logger LOG = Logger.getInstance("#com.intellij.designer.model.MetaManager");

  private final Map<String, MetaModel> myTag2Model = new HashMap<String, MetaModel>();
  private final Map<String, MetaModel> myTarget2Model = new HashMap<String, MetaModel>();
  private final List<Group> myPaletteGroups = new ArrayList<Group>();

  private PropertyChangeSupport myPaletteChangeSupport;

  private Map<Object, Object> myCache = new HashMap<Object, Object>();

  protected MetaManager(Project project, String name) {
    try {
      InputStream stream = getClass().getResourceAsStream(name);
      Document document = new SAXBuilder().build(stream);
      stream.close();

      Element rootElement = document.getRootElement();
      ClassLoader classLoader = getClass().getClassLoader();

      for (Object element : rootElement.getChildren(META)) {
        loadModel(classLoader, (Element)element);
      }

      for (Object element : rootElement.getChild(PALETTE).getChildren(GROUP)) {
        loadGroup(name, (Element)element);
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings("unchecked")
  private void loadModel(ClassLoader classLoader, Element element) throws Exception {
    String modelValue = element.getAttributeValue("model");
    Class<RadComponent> model = modelValue == null ? null : (Class<RadComponent>)classLoader.loadClass(modelValue);
    String target = element.getAttributeValue("class");
    String tag = element.getAttributeValue(TAG);

    MetaModel meta = new MetaModel(model, target, tag);

    String layout = element.getAttributeValue("layout");
    if (layout != null) {
      meta.setLayout((Class<RadLayout>)classLoader.loadClass(layout));
    }

    String delete = element.getAttributeValue("delete");
    if (delete != null) {
      meta.setDelete(Boolean.parseBoolean(delete));
    }

    Element presentation = element.getChild("presentation");
    if (presentation != null) {
      meta.setPresentation(presentation.getAttributeValue("title"), presentation.getAttributeValue("icon"));
    }

    Element palette = element.getChild("palette");
    if (palette != null) {
      meta.setPaletteItem(
        new Item(palette.getAttributeValue("title"), palette.getAttributeValue("icon"), palette.getAttributeValue("tooltip")));
    }

    Element creation = element.getChild("creation");
    if (creation != null) {
      meta.setCreation(creation.getTextTrim());
    }

    Element properties = element.getChild("properties");
    if (properties != null) {
      Attribute normal = properties.getAttribute("normal");
      if (normal != null) {
        meta.setNormalProperties(StringUtil.split(normal.getValue(), " "));
      }

      Attribute important = properties.getAttribute("important");
      if (important != null) {
        meta.setImportantProperties(StringUtil.split(important.getValue(), " "));
      }

      Attribute expert = properties.getAttribute("expert");
      if (expert != null) {
        meta.setExpertProperties(StringUtil.split(expert.getValue(), " "));
      }

      Attribute deprecated = properties.getAttribute("deprecated");
      if (deprecated != null) {
        meta.setDeprecatedProperties(StringUtil.split(deprecated.getValue(), " "));
      }
    }

    if (tag != null) {
      myTag2Model.put(tag, meta);
    }

    if (target != null) {
      myTarget2Model.put(target, meta);
    }
  }

  private void loadGroup(String tab, Element element) throws Exception {
    Group group = new Group(tab, element.getAttributeValue(NAME));

    for (Object child : element.getChildren(ITEM)) {
      String tag = ((Element)child).getAttributeValue(TAG);
      group.addItem(getModelByTag(tag).getPaletteItem());
    }

    myPaletteGroups.add(group);
  }

  @SuppressWarnings("unchecked")
  public <K, V> Map<K, V> getCache(Object key) {
    return (Map<K, V>)myCache.get(key);
  }

  public void setCache(Object key, Object value) {
    myCache.put(key, value);
  }

  @Nullable
  public MetaModel getModelByTag(String tag) {
    return myTag2Model.get(tag);
  }

  @Nullable
  public MetaModel getModelByTarget(String target) {
    return myTarget2Model.get(target);
  }

  public PaletteGroup[] getPaletteGroups() {
    return myPaletteGroups.toArray(new PaletteGroup[myPaletteGroups.size()]);
  }

  public void setPaletteChangeSupport(PropertyChangeSupport paletteChangeSupport) {
    myPaletteChangeSupport = paletteChangeSupport;
  }
}