package com.jetbrains.python;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  @NonNls public static final String PYTHON_MODULE = "PYTHON_MODULE";
  private final Icon myBigIcon = IconLoader.getIcon("/com/jetbrains/python/icons/python_24.png");
  private final Icon myOpenIcon = IconLoader.getIcon("/com/jetbrains/python/icons/pythonOpen.png");
  private final Icon myClosedIcon = IconLoader.getIcon("/com/jetbrains/python/icons/pythonClosed.png");

  protected PythonModuleTypeBase() {
    super(PYTHON_MODULE);
  }

  public String getName() {
    return "Python Module";
  }

  public String getDescription() {
    return "Provides facilities for developing Python, Django and Google App Engine applications";
  }

  public Icon getBigIcon() {
    return myBigIcon;
  }

  public Icon getNodeIcon(final boolean isOpened) {
    return isOpened ? myOpenIcon : myClosedIcon;
  }
}
