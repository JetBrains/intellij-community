package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.PlatformContentEntriesConfigurable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/**
 * @author yole
 */
public class PythonContentEntriesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable, OptionalConfigurable {
  private final Project myProject;

  public PythonContentEntriesConfigurable(final Project project) {
    super(project, "Project Structure", "reference.settingsdialog.project.structure");
    myProject = project;
  }

  @Nullable
  private static Module getSingleModule(Project project) {
    return ArrayUtil.getFirstElement(ModuleManager.getInstance(project).getModules());
  }

  @Override
  public boolean needDisplay() {
    return getSingleModule(myProject) != null;
  }

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(Module module) {
    return new PlatformContentEntriesConfigurable(module, JavaSourceRootType.SOURCE);
  }
}
