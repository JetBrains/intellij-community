package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 9, 2003
 * Time: 3:35:56 PM
 * To change this template use Options | File Templates.
 */
public class IdeConfigurablesGroup implements ConfigurableGroup {
  public String getDisplayName() {
    return "IDE Settings";
  }

  public String getShortName() {
    return "IDE";
  }

  public Configurable[] getConfigurables() {
    return ApplicationManager.getApplication().getComponents(Configurable.class);
  }

  public boolean equals(Object object) {
    return object instanceof IdeConfigurablesGroup;
  }

  public int hashCode() {
    return 0;
  }
}
