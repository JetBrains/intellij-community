package com.intellij.execution.junit;

import com.intellij.execution.RuntimeConfiguration;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit2.configuration.RunConfigurationModule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.Arrays;
import java.util.Collection;

public abstract class ModuleBasedConfiguration extends RuntimeConfiguration {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit.ModuleBasedConfiguration");
  private final RunConfigurationModule myModule;

  public ModuleBasedConfiguration(final String name,
                                  final RunConfigurationModule configurationModule, final ConfigurationFactory factory) {
    super(name, configurationModule.getProject(), factory);
    myModule = configurationModule;
  }

  public abstract Collection<Module> getValidModules();

  public void setModuleName(final String moduleName) {
    myModule.setModuleName(moduleName);
  }

  public RunConfigurationModule getConfigurationModule() {
    return myModule;
  }

  public void init() {
    myModule.init();
  }

  public void setModule(final Module module) {
    if (module == null) return;
    myModule.setModule(module);
  }

  public abstract void readExternal(Element element) throws InvalidDataException;
  public abstract void writeExternal(Element element) throws WriteExternalException;

  protected void readModule(final Element element) throws InvalidDataException {
    myModule.readExternal(element);
  }

  protected void writeModule(final Element element) throws WriteExternalException {
    myModule.writeExternal(element);
  }

  public Collection<Module> getAllModules() {
    return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
  }

  protected abstract ModuleBasedConfiguration createInstance();

  public ModuleBasedConfiguration clone() {
    final Element element = new Element("toClone");
    try {
      writeExternal(element);
      final ModuleBasedConfiguration configuration = createInstance();
      configuration.readExternal(element);
      return configuration;
    } catch (InvalidDataException e) {
      LOG.error(e);
      return null;
    } catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  public Module[] getModules() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      public Module[] compute() {
        final Module module = getConfigurationModule().getModule();
        return module == null ? null : new Module[] {module};
      }
    });
  }
}
