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
import com.intellij.openapi.options.colors.pages.GeneralColorsPage;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map.Entry;

import static com.intellij.application.options.colors.ColorAndFontOptions.selectOrEditColor;
import static com.intellij.openapi.application.ApplicationBundle.message;
import static com.intellij.openapi.util.text.StringUtil.naturalCompare;
import static com.intellij.util.ui.UIUtil.isUnderDarcula;
import static javax.swing.SwingConstants.LEFT;

/**
 * @author Sergey.Malenkov
 */
final class BreadcrumbsConfigurable implements Configurable {
  private final HashMap<String, JCheckBox> map = new HashMap<>();
  private JComponent component;
  private JCheckBox show;
  private JRadioButton above;
  private JRadioButton below;
  private JLabel placement;
  private JLabel languages;

  @Override
  public String getDisplayName() {
    return message("configurable.breadcrumbs");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.editor.general.breadcrumbs";
  }

  @Override
  public JComponent createComponent() {
    if (component == null) {
      for (BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensions()) {
        for (Language language : provider.getLanguages()) {
          String id = language.getID();
          if (!map.containsKey(id)) {
            map.put(id, new JCheckBox(language.getDisplayName()));
          }
        }
      }
      JPanel boxes = new JPanel(new GridLayout(0, 3, isUnderDarcula() ? JBUI.scale(10) : 0, 0));
      map.values().stream().sorted((box1, box2) -> naturalCompare(box1.getText(), box2.getText())).forEach(box -> boxes.add(box));

      show = new JCheckBox(message("checkbox.show.breadcrumbs"));
      show.addItemListener(event -> updateEnabled());

      above = new JRadioButton(message("radio.show.breadcrumbs.above"));
      below = new JRadioButton(message("radio.show.breadcrumbs.below"));

      ButtonGroup group = new ButtonGroup();
      group.add(above);
      group.add(below);

      placement = new JLabel(message("label.breadcrumbs.placement"));
      placement.setBorder(JBUI.Borders.emptyRight(12));

      JPanel placementPanel = new JPanel(new HorizontalLayout(0));
      placementPanel.setBorder(JBUI.Borders.emptyLeft(24));
      placementPanel.add(placement);
      placementPanel.add(above);
      placementPanel.add(below);

      languages = new JLabel(message("label.breadcrumbs.languages"));

      JPanel languagesPanel = new JPanel(new VerticalLayout(JBUI.scale(6)));
      languagesPanel.setBorder(JBUI.Borders.empty(0, 24, 12, 0));
      languagesPanel.add(languages);
      languagesPanel.add(boxes);

      component = new JPanel(new VerticalLayout(JBUI.scale(12), LEFT));
      component.add(show);
      component.add(placementPanel);
      component.add(languagesPanel);
      component.add(LinkLabel.create(message("configure.breadcrumbs.colors"), () -> {
        DataContext context = DataManager.getInstance().getDataContext(component);
        selectOrEditColor(context, "Breadcrumbs//Current", GeneralColorsPage.class);
      }));
    }
    return component;
  }

  @Override
  public void reset() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    setBreadcrumbsAbove(settings.isBreadcrumbsAbove());
    setBreadcrumbsShown(settings.isBreadcrumbsShown());
    for (Entry<String, JCheckBox> entry : map.entrySet()) {
      entry.getValue().setSelected(settings.isBreadcrumbsShownFor(entry.getKey()));
    }
    updateEnabled();
  }

  @Override
  public boolean isModified() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (isBreadcrumbsAbove() != settings.isBreadcrumbsAbove()) return true;
    if (isBreadcrumbsShown() != settings.isBreadcrumbsShown()) return true;
    for (Entry<String, JCheckBox> entry : map.entrySet()) {
      if (settings.isBreadcrumbsShownFor(entry.getKey()) != entry.getValue().isSelected()) return true;
    }
    return false;
  }

  @Override
  public void apply() {
    boolean modified = false;
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    if (settings.setBreadcrumbsAbove(isBreadcrumbsAbove())) modified = true;
    if (settings.setBreadcrumbsShown(isBreadcrumbsShown())) modified = true;
    for (Entry<String, JCheckBox> entry : map.entrySet()) {
      if (settings.setBreadcrumbsShownFor(entry.getKey(), entry.getValue().isSelected())) modified = true;
    }
    if (modified) UISettings.getInstance().fireUISettingsChanged();
  }

  private boolean isBreadcrumbsAbove() {
    return above != null && above.isSelected();
  }

  private void setBreadcrumbsAbove(boolean value) {
    JRadioButton button = value ? above : below;
    if (button != null) button.setSelected(true);
  }

  private boolean isBreadcrumbsShown() {
    return show != null && show.isSelected();
  }

  private void setBreadcrumbsShown(boolean value) {
    if (show != null) show.setSelected(value);
  }

  private void updateEnabled() {
    boolean enabled = isBreadcrumbsShown();
    if (above != null) above.setEnabled(enabled);
    if (below != null) below.setEnabled(enabled);
    if (placement != null) placement.setEnabled(enabled);
    if (languages != null) languages.setEnabled(enabled);
    for (JCheckBox box : map.values()) box.setEnabled(enabled);
  }
}
