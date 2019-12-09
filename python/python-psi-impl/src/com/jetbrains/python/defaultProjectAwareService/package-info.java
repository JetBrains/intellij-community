/**
 * For services that are configured by "Python integrated tools" window.
 * Each service stores it's state on 2 levels: app level (used as template for the new projects) and module level.
 * App settings are set by user for new project templates.
 * They are copied to new project.
 * For opening existing folder, autodetection is used with fallback to state from app level.
 * <p>
 * Inherit your abstract service from {@link com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareService},
 * create state and two children: for app and module.
 * Parametrize service.
 * Create "getInstance" and use {@link com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceClasses#getService(com.intellij.openapi.module.Module)}
 * and "createConfigurator" (return {@link com.jetbrains.python.defaultProjectAwareService.PyDefaultProjectAwareServiceModuleConfigurator ).
 * Parametrize configurator with function to autodetection if needed.
 * See usages for examples.
 */
package com.jetbrains.python.defaultProjectAwareService;
