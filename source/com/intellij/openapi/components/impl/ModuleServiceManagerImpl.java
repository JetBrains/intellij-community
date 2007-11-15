package com.intellij.openapi.components.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public class ModuleServiceManagerImpl extends ServiceManagerImpl {
  private static final ExtensionPointName<ServiceDescriptor> MODULE_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.moduleService");

  @SuppressWarnings({"UnusedDeclaration"})
  public ModuleServiceManagerImpl(Project project, Module module) {
    super(true);
    installEP(MODULE_SERVICES, module);
  }
}