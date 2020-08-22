// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PyBundle;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores buildout facet config data.
 * <br/>
 * User: dcheryasov
 */
public class BuildoutFacetConfiguration implements FacetConfiguration {

  public final static String ATTR_SCRIPT = "script";

  private String myScriptName;
  private List<String> myPaths;

  public BuildoutFacetConfiguration(String scriptName) {
    myScriptName = scriptName;
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {new Tab(editorContext.getModule())};
  }

  public String getScriptName() {
    return myScriptName;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myScriptName = JDOMExternalizerUtil.readField(element, ATTR_SCRIPT);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizerUtil.writeField(element, ATTR_SCRIPT, myScriptName);
  }

  /**
   * Sets the paths to be prepended to pythonpath, taken from a buildout script.
   * @param paths what to store; the list will be copied.
   */
  public void setPaths(@Nullable List<String> paths) {
    myPaths = paths == null ? null : new ArrayList<>(paths);
  }

  public List<String> getPaths() {
    return myPaths;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  private final class Tab extends FacetEditorTab {

    private final BuildoutConfigPanel myPanel;

    private Tab(Module module) {
      myPanel = new BuildoutConfigPanel(module, BuildoutFacetConfiguration.this);
    }

    @Nls
    @Override
  public String getDisplayName() {
    return PyBundle.message("configurable.Tab.display.name");
  }

    @NotNull
    @Override
    public JComponent createComponent() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(myPanel, BorderLayout.CENTER);
      panel.setBorder(JBUI.Borders.empty(5, 10, 10, 10));
      return panel;
    }

    @Override
    public boolean isModified() {
      return myPanel.isModified(BuildoutFacetConfiguration.this);
    }

    @Override
    public void reset() {
      myPanel.reset();
    }

    @Override
    public void apply() {
      myPanel.apply();
    }

    @Override
    public void disposeUIResources() { }

    @Override
    public String getHelpTopic() {
      return "reference-python-buildout";
    }
  }
}
