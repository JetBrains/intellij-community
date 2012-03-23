package com.jetbrains.python.packaging;

import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author vlan
 */
public class PyChooseRequirementsDialog extends ChooseElementsDialog<PyRequirement> {
  public PyChooseRequirementsDialog(@NotNull Project project, @NotNull List<? extends PyRequirement> requirements) {
    super(project, requirements, "Choose Packages to Install", "Choose one or more packages to install:");
  }

  @NotNull
  @Override
  protected String getItemText(@NotNull PyRequirement requirement) {
    return requirement.toString();
  }

  @Nullable
  @Override
  protected Icon getItemIcon(@NotNull PyRequirement requirement) {
    return null;
  }
}
