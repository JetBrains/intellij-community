package com.intellij.debugger.settings;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ide.DataManager;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Eugene Belyaev & Eugene Zhuravlev
 */
public class DebuggerConfigurable extends CompositeConfigurable implements ApplicationComponent {
  public DebuggerConfigurable() {
    super();
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableDebugger.png");
  }

  public String getDisplayName() {
    return "Debugger";
  }

  public String getHelpTopic() {
    return "project.propDebugger";
  }

  public String getComponentName() {
    return "DebuggerConfigurable";
  }

  protected List<Configurable> createConfigurables() {
    ArrayList<Configurable> configurables = new ArrayList<Configurable>();
    Project project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
    if(project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    configurables.add(new DebuggerGeneralConfigurable(project));
    configurables.add(new ViewsGeneralConfigurable());
    configurables.add(new NodeRendererConfigurable(project));
    return configurables;
  }
}