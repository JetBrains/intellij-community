package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class ApplicationManagerEx extends ApplicationManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.ex.ApplicationManagerEx");

  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx) ourApplication;
  }

  public static void createApplication(@NonNls String componentsDescriptor, boolean internal, boolean isUnitTestMode, boolean isHeadlessMode, @NonNls String appName) {
    new ApplicationImpl(componentsDescriptor, internal, isUnitTestMode, isHeadlessMode, appName);
  }

  public static void setApplication(Application instance) {
    ourApplication = instance;
  }
}
