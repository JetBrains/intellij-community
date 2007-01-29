package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 9, 2003
 * Time: 3:35:56 PM
 * To change this template use Options | File Templates.
 */
public class IdeConfigurablesGroup implements ConfigurableGroup {
  public String getDisplayName() {
    return OptionsBundle.message("ide.settings.display.name");
  }

  public String getShortName() {
    return OptionsBundle.message("ide.settings.short.name");
  }

  public Configurable[] getConfigurables() {
    final Application app = ApplicationManager.getApplication();
    final Configurable[] extensions = app.getExtensions(Configurable.APPLICATION_CONFIGURABLES);
    Configurable[] components = app.getComponents(Configurable.class);

    List<Configurable> result = new ArrayList<Configurable>();
    result.addAll(Arrays.asList(extensions));
    result.addAll(Arrays.asList(components));

    return result.toArray(new Configurable[result.size()]);
  }

  public boolean equals(Object object) {
    return object instanceof IdeConfigurablesGroup;
  }

  public int hashCode() {
    return 0;
  }
}
