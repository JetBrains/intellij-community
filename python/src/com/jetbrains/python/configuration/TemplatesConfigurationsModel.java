package com.jetbrains.python.configuration;

import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.templateLanguages.TemplatesService;

import java.util.List;

/**
 * User: catherine
 */
public class TemplatesConfigurationsModel extends CollectionComboBoxModel {
  private String myTemplateLanguage;
  private final TemplatesService myTemplatesService;

  public TemplatesConfigurationsModel(final List items, TemplatesService templatesService) {
    super(items, templatesService.getTemplateLanguage());
    myTemplatesService = templatesService;
    myTemplateLanguage = myTemplatesService.getTemplateLanguage();
  }

  public void reset() {
    setSelectedItem(myTemplateLanguage);
  }

  public void apply() {
    myTemplateLanguage = (String)getSelectedItem();
    myTemplatesService.setTemplateLanguage(myTemplateLanguage);
  }

  public Object getTemplateLanguage() {
    return myTemplateLanguage;
  }
}