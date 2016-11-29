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
import com.intellij.designer.palette.PaletteItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public abstract class MetaManager extends ModelLoader {
  private static final String META = "meta";
  private static final String PALETTE = "palette";
  private static final String GROUP = "group";
  private static final String NAME = "name";
  private static final String ITEM = "item";
  private static final String TAG = "tag";
  private static final String WRAP_IN = "wrap-in";

  private final Map<String, MetaModel> myTag2Model = new HashMap<>();
  private final Map<String, MetaModel> myTarget2Model = new HashMap<>();
  private final List<PaletteGroup> myPaletteGroups = new ArrayList<>();
  private final List<MetaModel> myWrapModels = new ArrayList<>();

  private Map<Object, Object> myCache = new HashMap<>();

  protected MetaManager(Project project, String name) {
    super(project);
    load(name);
  }

  @Override
  protected void loadDocument(Element rootElement) throws Exception {
    ClassLoader classLoader = getClass().getClassLoader();

    Map<MetaModel, List<String>> modelToMorphing = new HashMap<>();

    for (Element element : rootElement.getChildren(META)) {
      loadModel(classLoader, element, modelToMorphing);
    }

    for (Element element : rootElement.getChild(PALETTE).getChildren(GROUP)) {
      loadGroup(element);
    }

    Element wrapInElement = rootElement.getChild(WRAP_IN);
    if (wrapInElement != null) {
      for (Element element : wrapInElement.getChildren(ITEM)) {
        myWrapModels.add(myTag2Model.get(element.getAttributeValue("tag")));
      }
    }

    for (Map.Entry<MetaModel, List<String>> entry : modelToMorphing.entrySet()) {
      MetaModel meta = entry.getKey();
      List<MetaModel> morphingModels = new ArrayList<>();

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

  @NotNull
  @SuppressWarnings("unchecked")
  protected MetaModel loadModel(ClassLoader classLoader, Element element, Map<MetaModel, List<String>> modelToMorphing) throws Exception {
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

    return meta;
  }

  @NotNull
  protected MetaModel createModel(Class<RadComponent> model, String target, String tag) throws Exception {
    return new MetaModel(model, target, tag);
  }

  @NotNull
  protected DefaultPaletteItem createPaletteItem(Element palette) {
    return new DefaultPaletteItem(palette);
  }

  @NotNull
  protected VariationPaletteItem createVariationPaletteItem(PaletteItem paletteItem, MetaModel model, Element itemElement) {
    return new VariationPaletteItem(paletteItem, model, itemElement);
  }

  @NotNull
  protected PaletteGroup createPaletteGroup(String name) {
    return new PaletteGroup(name);
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

  @NotNull
  protected PaletteGroup loadGroup(Element element) throws Exception {
    PaletteGroup group = createPaletteGroup(element.getAttributeValue(NAME));

    for (Element itemElement : element.getChildren(ITEM)) {
      MetaModel model = getModelByTag(itemElement.getAttributeValue(TAG));
      PaletteItem paletteItem = model.getPaletteItem();

      if (!itemElement.getChildren().isEmpty()) {
        // Replace the palette item shown in the palette; it might provide a custom
        // title, icon or creation logic (and this is done here rather than in the
        // default palette item, since when loading elements back from XML, there's
        // no variation matching. We don't want for example to call the default
        // LinearLayout item "LinearLayout (Horizontal)", since that item would be
        // shown in the component tree for any <LinearLayout> found in the XML, including
        // those which set orientation="vertical". In the future, consider generalizing
        // this such that the {@link MetaModel} can hold multiple {@link PaletteItem}
        // instances, and perform attribute matching.
        if (itemElement.getAttribute("title") != null) {
          paletteItem = createVariationPaletteItem(paletteItem, model, itemElement);
        }
        group.addItem(paletteItem);

        for (Element grandChild : itemElement.getChildren(ITEM)) {
          group.addItem(createVariationPaletteItem(paletteItem, model, grandChild));
        }
      }
      else {
        group.addItem(paletteItem);
      }
    }

    myPaletteGroups.add(group);

    return group;
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