package com.intellij.application.options.pathMacros;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author dsl
 */
public class PathMacroConfigurable implements SearchableConfigurable, ApplicationComponent {
  public static final Icon ICON = IconLoader.getIcon("/general/pathVariables.png");
  @NonNls
  public static final String HELP_ID = "preferences.pathVariables";
  private PathMacroListEditor myEditor;
  private GlassPanel myGlassPanel;

  public JComponent createComponent() {
    myEditor = new PathMacroListEditor();
    myGlassPanel = new GlassPanel(myEditor.getPanel());
    return myEditor.getPanel();
  }

  public void apply() throws ConfigurationException {
    myEditor.commit();
  }

  public void reset() {
    myEditor.getPanel().getRootPane().setGlassPane(myGlassPanel);
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

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(myEditor.getPanel(), option, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}
