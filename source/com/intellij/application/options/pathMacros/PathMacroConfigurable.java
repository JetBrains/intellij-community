package com.intellij.application.options.pathMacros;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationBundle;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class PathMacroConfigurable implements Configurable, ApplicationComponent {
  public static final Icon ICON = IconLoader.getIcon("/general/pathVariables.png");
  @NonNls
  public static final String HELP_ID = "preferences.pathVariables";
  private PathMacroListEditor myEditor;

  public JComponent createComponent() {
    myEditor = new PathMacroListEditor();
    return myEditor.getPanel();
  }

  public void apply() throws ConfigurationException {
    myEditor.commit();
  }

  public void reset() {
    myEditor.reset();
  }

  public void disposeUIResources() {
    myEditor = null;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.path.variables");
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public boolean isModified() {
    return myEditor.isModified();
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getComponentName() {
    return "PathMacroConfigurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

}
