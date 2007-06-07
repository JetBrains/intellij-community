package com.intellij.codeEditor.printing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;

@State(
  name = "ExportToHTMLSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$PROJECT_FILE$"
    )}
)
public class ExportToHTMLSettings implements PersistentStateComponent<ExportToHTMLSettings> {
  public boolean PRINT_LINE_NUMBERS;
  public boolean OPEN_IN_BROWSER;
  @NonNls public String OUTPUT_DIRECTORY;

  private int myPrintScope;

  private boolean isIncludeSubpackages = false;
  private boolean isGenerateHyperlinksToClasses = false;

  public static ExportToHTMLSettings getInstance(Project project) {
    return ServiceManager.getService(project, ExportToHTMLSettings.class);
  }

  public int getPrintScope() {
    return myPrintScope;
  }

  public void setPrintScope(int printScope) {
    myPrintScope = printScope;
  }

  public boolean isIncludeSubdirectories() {
    return isIncludeSubpackages;
  }

  public void setIncludeSubpackages(boolean includeSubpackages) {
    isIncludeSubpackages = includeSubpackages;
  }

  public boolean isGenerateHyperlinksToClasses() {
    return isGenerateHyperlinksToClasses;
  }

  public void setGenerateHyperlinksToClasses(boolean generateHyperlinksToClasses) {
    isGenerateHyperlinksToClasses = generateHyperlinksToClasses;
  }

  public ExportToHTMLSettings getState() {
    return this;
  }

  public void loadState(ExportToHTMLSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
