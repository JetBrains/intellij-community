package com.jetbrains.python;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  @NonNls public static final String PYTHON_MODULE = "PYTHON_MODULE";
  private final Icon myBigIcon = PythonIcons.Python.Icons.Python_24;
  private final Icon myOpenIcon = PythonIcons.Python.Icons.PythonOpen;
  private final Icon myClosedIcon = PythonIcons.Python.Icons.PythonClosed;

  protected PythonModuleTypeBase() {
    super(PYTHON_MODULE);
  }

  public String getName() {
    return "Python Module";
  }

  public String getDescription() {
    return "Python modules are used for developing <b>Python</b> applications. Supported technologies include <b>Django, Google App Engine, Mako, Jinja2</b> and others.";
  }

  public Icon getBigIcon() {
    return myBigIcon;
  }

  public Icon getNodeIcon(final boolean isOpened) {
    return isOpened ? myOpenIcon : myClosedIcon;
  }
}
