package com.jetbrains.python.templateLanguages;

import com.jetbrains.python.newProject.PyNewProjectSettings;
import org.jetbrains.annotations.Nullable;

public class TemplateSettingsHolder extends PyNewProjectSettings {
  private String myTemplatesFolder;
  private String myTemplateLanguage;

  public String getTemplatesFolder() {
    return myTemplatesFolder;
  }

  public void setTemplatesFolder(String templatesFolder) {
    myTemplatesFolder = templatesFolder;
  }

  @Nullable
  public String getTemplateLanguage() {
    return myTemplateLanguage;
  }

  public void setTemplateLanguage(@Nullable String templateLanguage) {
    myTemplateLanguage = templateLanguage;
  }
}
