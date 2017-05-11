/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.colors.pages.GeneralColorsPage;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import static com.intellij.application.options.colors.ColorAndFontOptions.selectOrEditColor;
import static com.intellij.openapi.application.ApplicationBundle.message;

/**
 * @author Sergey.Malenkov
 */
final class BreadcrumbsConfigurable implements Configurable {
  private final LinkedHashMap<String, JCheckBox> map = new LinkedHashMap<>();
  private JComponent component;
  private JCheckBox box;

  @Override
  public String getDisplayName() {
    return message("configurable.breadcrumbs");
  }

  @Override
  public JComponent createComponent() {
    if (component == null) {
      box = new JCheckBox(message("checkbox.show.breadcrumbs"));
      box.addItemListener(event -> updateEnabled());

      JPanel upper = new JPanel(new HorizontalLayout(JBUI.scale(20)));
      upper.add(box);
      upper.add(LinkLabel.create(message("configure.breadcrumbs.colors"), () -> {
        DataContext context = DataManager.getInstance().getDataContext(component);
        selectOrEditColor(context, "Breadcrumbs//Current", GeneralColorsPage.class);
      }));

      JPanel boxes = new JPanel(new VerticalLayout(0));
      boxes.setBorder(JBUI.Borders.emptyLeft(20));
      for (BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensions()) {
        for (Language language : provider.getLanguages()) {
          String id = language.getID();
          if (!map.containsKey(id)) {
            JCheckBox box = new JCheckBox(language.getDisplayName());
            boxes.add(box);
            map.put(id, box);
          }
        }
      }
      component = new JPanel(new VerticalLayout(0));
      component.add(upper);
      component.add(boxes);
    }
    return component;
  }

  @Override
  public void reset() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (box != null) box.setSelected(settings.isBreadcrumbsShown());
    for (Entry<String, JCheckBox> entry : map.entrySet()) {
      entry.getValue().setSelected(settings.isBreadcrumbsShownFor(entry.getKey()));
    }
    updateEnabled();
  }

  @Override
  public boolean isModified() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (box != null && box.isSelected() != settings.isBreadcrumbsShown()) return true;
    for (Entry<String, JCheckBox> entry : map.entrySet()) {
      if (settings.isBreadcrumbsShownFor(entry.getKey()) != entry.getValue().isSelected()) return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean modified = false;
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (box != null && settings.setBreadcrumbsShown(box.isSelected())) modified = true;
    for (Entry<String, JCheckBox> entry : map.entrySet()) {
      if (settings.setBreadcrumbsShownFor(entry.getKey(), entry.getValue().isSelected())) modified = true;
    }
    if (modified) UISettings.getInstance().fireUISettingsChanged();
  }

  private void updateEnabled() {
    for (JCheckBox box : map.values()) box.setEnabled(this.box.isSelected());
  }
}
