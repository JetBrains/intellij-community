package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;

/**
 *  @author dsl
 */
public class ModuleRootEventImpl extends ModuleRootEvent {
  private final boolean myFiletypes;

  public ModuleRootEventImpl (final Project project, boolean filetypes) {
    super(project);
    myFiletypes = filetypes;
  }

  public boolean isCausedByFileTypesChange() {
    return myFiletypes;
  }

}
