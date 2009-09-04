package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.plugin.ui.*;
import org.jetbrains.annotations.NotNull;

/**
 * Structural search plugin main class.
 */
public final class StructuralSearchPlugin implements ProjectComponent, JDOMExternalizable {
  private boolean searchInProgress;
  private boolean replaceInProgress;
  private final ConfigurationManager myConfigurationManager = new ConfigurationManager();
  private ExistingTemplatesComponent myExistingTemplatesComponent;

  public boolean isSearchInProgress() {
    return searchInProgress;
  }

  public void setSearchInProgress(boolean searchInProgress) {
    this.searchInProgress = searchInProgress;
  }

  public boolean isReplaceInProgress() {
    return replaceInProgress;
  }

  public void setReplaceInProgress(boolean replaceInProgress) {
    this.replaceInProgress = replaceInProgress;
  }

  /**
   * Method is called after plugin is already created and configured. Plugin can start to communicate with
   * other plugins only in this method.
   */
  public void initComponent() {
  }

  /**
   * This method is called on plugin disposal.
   */
  public void disposeComponent() {
  }

  /**
   * Returns the name of component
   *
   * @return String representing component name. Use PluginName.ComponentName notation
   *  to avoid conflicts.
   */
  @NotNull
  public String getComponentName() {
    return "StructuralSearchPlugin";
  }

  // Simple logging facility
  private static Logger logger;

  // Logs given string to IDEA logger
  public static void debug(String str) {
    if (logger==null) logger = Logger.getInstance("Structural search");
    logger.info(str);
  }

  public void readExternal(org.jdom.Element element) {
    myConfigurationManager.loadConfigurations(element);
  }

  public void writeExternal(org.jdom.Element element) {
    myConfigurationManager.saveConfigurations(element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public static StructuralSearchPlugin getInstance(Project project) {
    return project.getComponent(StructuralSearchPlugin.class);
  }

  public ConfigurationManager getConfigurationManager() {
    return myConfigurationManager;
  }

  public ExistingTemplatesComponent getExistingTemplatesComponent() {
    return myExistingTemplatesComponent;
  }

  public void setExistingTemplatesComponent(ExistingTemplatesComponent existingTemplatesComponent) {
    myExistingTemplatesComponent = existingTemplatesComponent;
  }
}
