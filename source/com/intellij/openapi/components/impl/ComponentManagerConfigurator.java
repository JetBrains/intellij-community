package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.DOMUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NonNls;
import org.w3c.dom.Document;

import java.net.URL;
import java.util.Map;

class ComponentManagerConfigurator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.ComponentManagerConfigurator");

  private static final Map<String, ComponentManagerConfig> ourDescriptorToConfig = new HashMap<String, ComponentManagerConfig>();

  private final ComponentManagerImpl myComponentManager;

  public ComponentManagerConfigurator(final ComponentManagerImpl componentManager) {
    myComponentManager = componentManager;
  }

  private void loadConfiguration(final ComponentConfig[] configs, final boolean loadDummies, final IdeaPluginDescriptor descriptor) {
    final boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();

    for (ComponentConfig config : configs) {
      if (!loadDummies && config.skipForDummyProject) {
        continue;
      }

      String interfaceClass = config.interfaceClass;
      String implClass = config.implementationClass;
      if (headless) {
        String headlessImplClass = config.headlessImplementationClass;
        if (headlessImplClass != null) {
          if (headlessImplClass.trim().length() == 0) continue;
          implClass = headlessImplClass;
        }
      }

      if (interfaceClass == null) interfaceClass = implClass;

      Map<String, String> options = config.options;

      //todo: isComponentSuitable should be moved somewhere
      if (!myComponentManager.isComponentSuitable(options)) continue;

      ClassLoader loader = null;
      if (descriptor != null) {
        loader = descriptor.getPluginClassLoader();
      }
      if (loader == null) {
        loader = myComponentManager.getClass().getClassLoader();
      }

      interfaceClass = interfaceClass.trim();
      implClass = implClass.trim();

      try {
        myComponentManager
          .registerComponent(Class.forName(interfaceClass, true, loader), Class.forName(implClass, true, loader), options, true,
                          isTrue(options, "lazy"));
      }
      catch (Exception e) {
        @NonNls final String message = "Error while initializing component: " + interfaceClass + ":" + implClass;

        if (descriptor != null) {
          LOG.error(message, new PluginException(e, descriptor.getPluginId()));
        }
        else {
          LOG.error(message, e);
        }
      }
      catch (Error e) {
        if (descriptor != null) {
          LOG.error(new PluginException(e, descriptor.getPluginId()));
        }
        else {
          throw e;
        }
      }
    }
  }

  private void loadComponentsConfiguration(String descriptor, String layer, boolean loadDummies) {
    try {
      ComponentManagerConfig managerConfig = ourDescriptorToConfig.get(descriptor);

      if (managerConfig == null) {
        final URL url = DecodeDefaultsUtil.getDefaults(this, descriptor);

        assert url != null : "Defaults not found:" + descriptor;

        final Document document = DOMUtil.load(url);

        managerConfig = XmlSerializer.deserialize(document.getDocumentElement(), ComponentManagerConfig.class);

        ourDescriptorToConfig.put(descriptor, managerConfig);
      }

      assert managerConfig != null;
      ComponentConfig[] componentConfigs;

      if (layer.equals(ComponentManagerConfig.APPLICATION_COMPONENTS)) {
        componentConfigs = managerConfig.applicationComponents;
      }
      else if (layer.equals(ComponentManagerConfig.PROJECT_COMPONENTS)) {
        componentConfigs = managerConfig.projectComponents;
      }
      else if (layer.equals(ComponentManagerConfig.MODULE_COMPONENTS)) {
        componentConfigs = managerConfig.moduleComponents;
      }
      else {
        throw new IllegalArgumentException("Unsupported layer: "+ layer);
      }

      loadComponentsConfiguration(componentConfigs, null, loadDummies);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void loadComponentsConfiguration(String layer, final boolean loadDummies) {
    loadComponentsConfiguration(ApplicationManagerEx.getApplicationEx().getComponentsDescriptor(), layer, loadDummies);
  }

  private static boolean isTrue(Map options, @NonNls final String option) {
    return options != null && options.containsKey(option) && Boolean.valueOf(options.get(option).toString()).booleanValue();
  }

  public void loadComponentsConfiguration(final ComponentConfig[] components,
                                          final IdeaPluginDescriptor descriptor,
                                          final boolean loadDummies) {
    if (components == null) return;

    loadConfiguration(components, loadDummies, descriptor);
  }
}
