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

import com.intellij.designer.palette.DefaultPaletteItem;
import com.intellij.designer.palette.PaletteGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.Nullable;

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
  private static final String WRAP_IN = "wrap-in";

  protected static final Logger LOG = Logger.getInstance("#com.intellij.designer.model.MetaManager");

  private final Map<String, MetaModel> myTag2Model = new HashMap<String, MetaModel>();
  private final Map<String, MetaModel> myTarget2Model = new HashMap<String, MetaModel>();
  private final List<PaletteGroup> myPaletteGroups = new ArrayList<PaletteGroup>();
  private final List<MetaModel> myWrapModels = new ArrayList<MetaModel>();

  private Map<Object, Object> myCache = new HashMap<Object, Object>();

  protected MetaManager(Project project, String name) {
    try {
      InputStream stream = getClass().getResourceAsStream(name);
      Document document = new SAXBuilder().build(stream);
      stream.close();

      loadDocument(document.getRootElement());
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  protected void loadDocument(Element rootElement) throws Exception {
    ClassLoader classLoader = getClass().getClassLoader();

    Map<MetaModel, List<String>> modelToMorphing = new HashMap<MetaModel, List<String>>();

    for (Object element : rootElement.getChildren(META)) {
      loadModel(classLoader, (Element)element, modelToMorphing);
    }

    for (Object element : rootElement.getChild(PALETTE).getChildren(GROUP)) {
      loadGroup((Element)element);
    }

    Element wrapInElement = rootElement.getChild(WRAP_IN);
    if (wrapInElement != null) {
      for (Object element : wrapInElement.getChildren(ITEM)) {
        Element item = (Element)element;
        myWrapModels.add(myTag2Model.get(item.getAttributeValue("tag")));
      }
    }

    for (Map.Entry<MetaModel, List<String>> entry : modelToMorphing.entrySet()) {
      MetaModel meta = entry.getKey();
      List<MetaModel> morphingModels = new ArrayList<MetaModel>();

      for (String tag : entry.getValue()) {
        MetaModel morphingModel = myTag2Model.get(tag);
        if (morphingModel != null) {
          morphingModels.add(morphingModel);
        }
      }

      if (!morphingModels.isEmpty()) {
        meta.setMorphingModels(morphingModels);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void loadModel(ClassLoader classLoader, Element element, Map<MetaModel, List<String>> modelToMorphing) throws Exception {
    String modelValue = element.getAttributeValue("model");
    Class<RadComponent> model = modelValue == null ? null : (Class<RadComponent>)classLoader.loadClass(modelValue);
    String target = element.getAttributeValue("class");
    String tag = element.getAttributeValue(TAG);

    MetaModel meta = createModel(model, target, tag);

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
      meta.setPaletteItem(createPaletteItem(palette));
    }

    Element creation = element.getChild("creation");
    if (creation != null) {
      meta.setCreation(creation.getTextTrim());
    }

    Element properties = element.getChild("properties");
    if (properties != null) {
      loadProperties(meta, properties);
    }

    Element morphing = element.getChild("morphing");
    if (morphing != null) {
      modelToMorphing.put(meta, StringUtil.split(morphing.getAttribute("to").getValue(), " "));
    }

    loadOther(meta, element);

    if (tag != null) {
      myTag2Model.put(tag, meta);
    }

    if (target != null) {
      myTarget2Model.put(target, meta);
    }
  }

  protected MetaModel createModel(Class<RadComponent> model, String target, String tag) throws Exception {
    return new MetaModel(model, target, tag);
  }

  protected DefaultPaletteItem createPaletteItem(Element palette) {
    return new DefaultPaletteItem(palette);
  }

  protected void loadProperties(MetaModel meta, Element properties) throws Exception {
    Attribute inplace = properties.getAttribute("inplace");
    if (inplace != null) {
      meta.setInplaceProperties(StringUtil.split(inplace.getValue(), " "));
    }

    Attribute top = properties.getAttribute("top");
    if (top != null) {
      meta.setTopProperties(StringUtil.split(top.getValue(), " "));
    }

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

  protected void loadOther(MetaModel meta, Element element) throws Exception {
  }

  private void loadGroup(Element element) throws Exception {
    PaletteGroup group = new PaletteGroup(element.getAttributeValue(NAME));

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

  public List<MetaModel> getWrapInModels() {
    return myWrapModels;
  }

  public List<PaletteGroup> getPaletteGroups() {
    return myPaletteGroups;
  }
}