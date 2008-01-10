/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.JavaPsiFacade;

public abstract class JavaPsiFacadeEx extends JavaPsiFacade {
  public static JavaPsiFacadeEx getInstanceEx(Project project) {
    return (JavaPsiFacadeEx)getInstance(project);
  }

  public abstract void setAssertOnFileLoadingFilter(final VirtualFileFilter filter);
}