// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.colors.pages.GeneralColorsPage;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import static com.intellij.application.options.colors.ColorAndFontOptions.selectOrEditColor;
import static com.intellij.openapi.application.ApplicationBundle.message;
import static com.intellij.openapi.util.text.StringUtil.naturalCompare;
import static com.intellij.util.containers.ContainerUtil.newSmartList;
import static com.intellij.util.ui.UIUtil.isUnderDarcula;
import static javax.swing.SwingConstants.LEFT;

/**
 * @author Sergey.Malenkov
 */
final class BreadcrumbsConfigurable extends CompositeConfigurable<BreadcrumbsConfigurable.BreadcrumbsProviderConfigurable> implements SearchableConfigurable {
  private final HashMap<String, JCheckBox> map = new HashMap<>();
  private JComponent component;
  private JCheckBox show;
  private JRadioButton above;
  private JRadioButton below;
  private JLabel placement;
  private JLabel languages;

  @NotNull
  @Override
  public String getId() {
    return "editor.breadcrumbs";
  }

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
      for (final BreadcrumbsProviderConfigurable configurable : getConfigurables()) {
        final String id = configurable.getId();
        if (!map.containsKey(id)) {
          map.put(id, configurable.createComponent());
        }
      }
      JPanel boxes = new JPanel(new GridLayout(0, 3, isUnderDarcula() ? JBUIScale.scale(10) : 0, 0));
      map.values().stream().sorted((box1, box2) -> naturalCompare(box1.getText(), box2.getText())).forEach(box -> boxes.add(box));

      show = new JCheckBox(message("checkbox.show.breadcrumbs"));
      show.addItemListener(event -> updateEnabled());

      above = new JRadioButton(message("radio.show.breadcrumbs.above"));
      below = new JRadioButton(message("radio.show.breadcrumbs.below"));

      ButtonGroup group = new ButtonGroup();
      group.add(above);
      group.add(below);

      placement = new JLabel(message("label.breadcrumbs.placement"));

      JPanel placementPanel = new JPanel(new HorizontalLayout(JBUIScale.scale(UIUtil.DEFAULT_HGAP)));
      placementPanel.setBorder(JBUI.Borders.emptyLeft(24));
      placementPanel.add(placement);
      placementPanel.add(above);
      placementPanel.add(below);

      languages = new JLabel(message("label.breadcrumbs.languages"));

      JPanel languagesPanel = new JPanel(new VerticalLayout(JBUIScale.scale(6)));
      languagesPanel.setBorder(JBUI.Borders.empty(0, 24, 12, 0));
      languagesPanel.add(languages);
      languagesPanel.add(boxes);

      component = new JPanel(new VerticalLayout(JBUIScale.scale(12), LEFT));
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

  @NotNull
  @Override
  protected List<BreadcrumbsProviderConfigurable> createConfigurables() {
    final List<BreadcrumbsProviderConfigurable> configurables = newSmartList();
    for (final BreadcrumbsProvider provider : BreadcrumbsProvider.EP_NAME.getExtensionList()) {
      for (final Language language : provider.getLanguages()) {
        configurables.add(new BreadcrumbsProviderConfigurable(provider, language));
      }
    }
    return configurables;
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

  static class BreadcrumbsProviderConfigurable implements SearchableConfigurable {

    private final BreadcrumbsProvider myProvider;
    private final Language myLanguage;

    private BreadcrumbsProviderConfigurable(@NotNull final BreadcrumbsProvider provider, @NotNull final Language language) {
      myProvider = provider;
      myLanguage = language;
    }

    @Nullable
    @Override
    public JCheckBox createComponent() {
      return new JCheckBox(myLanguage.getDisplayName());
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() throws ConfigurationException {
    }

    @NotNull
    @Override
    public String getId() {
      return myLanguage.getID();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
      return myLanguage.getDisplayName();
    }

    @NotNull
    @Override
    public Class<?> getOriginalClass() {
      return myProvider.getClass();
    }
  }
}
