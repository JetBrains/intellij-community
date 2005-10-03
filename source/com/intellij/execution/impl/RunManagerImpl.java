package com.intellij.execution.impl;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.ModuleBasedConfiguration;
import com.intellij.execution.util.RefactoringElementListenerComposite;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.util.containers.InternalIterator;
import com.intellij.util.containers.CollectUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.ide.util.PropertiesComponent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;


public class RunManagerImpl extends RunManagerEx implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.RunManager");

  private final Project myProject;

  private Map<String, ConfigurationType> myTypesByName = new LinkedHashMap<String, ConfigurationType>();

  private List<RunnerAndConfigurationSettingsImpl> myConfigurations = new ArrayList<RunnerAndConfigurationSettingsImpl>(); // template configurations are not included here
  private Map<ConfigurationFactory, RunnerAndConfigurationSettingsImpl> myTemplateConfigurationsMap = new HashMap<ConfigurationFactory, RunnerAndConfigurationSettingsImpl>();
  private ConfigurationType myActiveConfigurationType;
  private Map<ConfigurationType, RunnerAndConfigurationSettingsImpl> mySelectedConfigurations = new HashMap<ConfigurationType, RunnerAndConfigurationSettingsImpl>();

  protected MyRefactoringElementListenerProvider myRefactoringElementListenerProvider;
  private RunnerAndConfigurationSettingsImpl myTempConfiguration;

  @NonNls
  private static final String ACTIVE_TYPE = "activeType";
  @NonNls
  private static final String TEMP_CONFIGURATION = "tempConfiguration";
  @NonNls
  private static final String CONFIGURATION = "configuration";
  private ConfigurationType[] myTypes;
  private final RunManagerConfig myConfig;
  @NonNls
  protected static final String NAME_ATTR = "name";
  @NonNls
  protected static final String SELECTED_ATTR = "selected";

  public RunManagerImpl(final Project project,
                        PropertiesComponent propertiesComponent,
                        ConfigurationType[] configurationTypes) {
    myConfig = new RunManagerConfig(propertiesComponent);
    myProject = project;
    myRefactoringElementListenerProvider = new MyRefactoringElementListenerProvider();
    initializeConfigurationTypes(configurationTypes);

    for (Iterator<RunnerAndConfigurationSettingsImpl> iterator = myConfigurations.iterator(); iterator.hasNext();) {
      final RunnerAndConfigurationSettings settings = iterator.next();
      RunConfiguration configuration = settings.getConfiguration();
      if (configuration instanceof ModuleBasedConfiguration) {
        ((ModuleBasedConfiguration)configuration).init();
      }
    }
  }

  // separate method needed for tests
  public final void initializeConfigurationTypes(final ConfigurationType[] factories) {
    LOG.assertTrue(factories != null);

    myTypes = factories;
    for (int i = 0; i < factories.length; i++) {
      final ConfigurationType type = factories[i];
      myTypesByName.put(type.getComponentName(), type);
    }
  }

  public void disposeComponent() { }

  public void initComponent() {
  }

  public void projectOpened() {
    installRefactoringListener();
  }

  // separate method needed for tests
  public void installRefactoringListener() {
    RefactoringListenerManager.getInstance(myProject).addListenerProvider(myRefactoringElementListenerProvider);
  }

  // separate method needed for tests
  public void uninstallRefactoringListener() {
    RefactoringListenerManager.getInstance(myProject).removeListenerProvider(myRefactoringElementListenerProvider);
  }

  public RunnerAndConfigurationSettingsImpl createConfiguration(final String name, final ConfigurationFactory factory) {
    LOG.assertTrue(name != null);
    LOG.assertTrue(factory != null);
    RunnerAndConfigurationSettingsImpl template = getConfigurationTemplate(factory);
    RunConfiguration configuration = factory.createConfiguration(name, template.getConfiguration());
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(this, configuration, false);
    settings.importRunnerAndConfigurationSettings(template);
    return settings;
  }

  public void projectClosed() {
    uninstallRefactoringListener();
  }


  public RunManagerConfig getConfig() {
    return myConfig;
  }

  public ConfigurationType[] getConfigurationFactories() {
    return (ConfigurationType[])myTypes.clone();
  }

  /**
   * Template configuration is not included
   */
  public RunConfiguration[] getConfigurations(final ConfigurationType type) {
    LOG.assertTrue(type != null);

    final List<RunConfiguration> array = new ArrayList<RunConfiguration>();
    for (int i = 0; i < myConfigurations.size(); i++) {
      final RunConfiguration configuration = myConfigurations.get(i).getConfiguration();
      if (type.equals(configuration.getType())) {
        array.add(configuration);
      }
    }
    return array.toArray(new RunConfiguration[array.size()]);
  }

  public RunConfiguration[] getAllConfigurations() {
    RunConfiguration [] result = new RunConfiguration[myConfigurations.size()];
    int i = 0;
    for (Iterator<RunnerAndConfigurationSettingsImpl> iterator = myConfigurations.iterator(); iterator.hasNext();i++) {
      RunnerAndConfigurationSettings settings = iterator.next();
      result[i] = settings.getConfiguration();
    }

    return result;
  }

  public RunnerAndConfigurationSettings getSettings(RunConfiguration configuration){
    for (Iterator<RunnerAndConfigurationSettingsImpl> iterator = myConfigurations.iterator(); iterator.hasNext();) {
      RunnerAndConfigurationSettings settings = iterator.next();
      if(settings.getConfiguration() == configuration) return settings;
    }
    return null;
  }

  /**
   * Template configuration is not included
   */
  public RunnerAndConfigurationSettingsImpl[] getConfigurationSettings(final ConfigurationType type) {
    LOG.assertTrue(type != null);

    final List<RunnerAndConfigurationSettings> array = new ArrayList<RunnerAndConfigurationSettings>();
    for (int i = 0; i < myConfigurations.size(); i++) {
      final RunnerAndConfigurationSettingsImpl configuration = myConfigurations.get(i);
      if (type.equals(configuration.getType())) {
        array.add(configuration);
      }
    }
    return array.toArray(new RunnerAndConfigurationSettingsImpl[array.size()]);
  }

  public RunnerAndConfigurationSettingsImpl getConfigurationTemplate(final ConfigurationFactory factory) {
    RunnerAndConfigurationSettingsImpl template = myTemplateConfigurationsMap.get(factory);
    if (template == null) {
      template = new RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(myProject), true);
      myTemplateConfigurationsMap.put(factory, template);
    }
    return template;
  }

  public void addConfiguration(RunnerAndConfigurationSettingsImpl settings) {
    myConfigurations.add(settings);
  }

  public void removeConfigurations(final ConfigurationType type) {
    LOG.assertTrue(type != null);

    //for (Iterator<Pair<RunConfiguration, JavaProgramRunner>> it = myRunnerPerConfigurationSettings.keySet().iterator(); it.hasNext();) {
    //  final Pair<RunConfiguration, JavaProgramRunner> pair = it.next();
    //  if (type.equals(pair.getFirst().getType())) {
    //    it.remove();
    //  }
    //}
    for (Iterator<RunnerAndConfigurationSettingsImpl> it = myConfigurations.iterator(); it.hasNext();) {
      final RunnerAndConfigurationSettings configuration = it.next();
      if (type.equals(configuration.getType())) {
        it.remove();
      }
    }

    if (myTempConfiguration != null && type.equals(myTempConfiguration.getType())) {
      myTempConfiguration = null;
    }
  }

  public ConfigurationType getActiveConfigurationFactory() {
    return myActiveConfigurationType;
  }

  public void setActiveConfigurationFactory(final ConfigurationType activeConfigurationType) {
    LOG.assertTrue(activeConfigurationType != null);
    myActiveConfigurationType = activeConfigurationType;
  }

  public RunnerAndConfigurationSettingsImpl getSelectedConfiguration(final ConfigurationType type) {
    LOG.assertTrue(type != null);
    return mySelectedConfigurations.get(type);
  }

  public void setSelectedConfiguration(final RunnerAndConfigurationSettingsImpl configuration) {
    LOG.assertTrue(configuration != null);
    mySelectedConfigurations.put(configuration.getType(), configuration);
  }

  public void clearConfigurationSelection(final ConfigurationType configurationType) {
    LOG.assertTrue(configurationType != null);
    mySelectedConfigurations.remove(configurationType);
  }

  public static boolean canRunConfiguration(final RunConfiguration configuration) {
    LOG.assertTrue(configuration != null);
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationError er) {
      return false;
    }
    catch (RuntimeConfigurationException e) {
      return true;
    }
    return true;
  }

  public void writeExternal(final Element parentNode) throws WriteExternalException {
    LOG.assertTrue(parentNode != null);
    if (myActiveConfigurationType != null) {
      final Element element = new Element(ACTIVE_TYPE);
      parentNode.addContent(element);
      element.setAttribute(NAME_ATTR, myActiveConfigurationType.getComponentName());
    }

    if (myTempConfiguration != null) {
      addConfigurationElement(parentNode, myTempConfiguration, TEMP_CONFIGURATION);
    }

    for (Iterator<RunnerAndConfigurationSettingsImpl> iterator = myTemplateConfigurationsMap.values().iterator(); iterator.hasNext();) {
      addConfigurationElement(parentNode, iterator.next(), CONFIGURATION);
    }

    final List<RunnerAndConfigurationSettingsImpl> stableConfigurations = getStableConfigurations();
    for (int i = 0; i < stableConfigurations.size(); i++) {
      addConfigurationElement(parentNode, stableConfigurations.get(i), CONFIGURATION);
    }
  }

  private void addConfigurationElement(final Element parentNode, RunnerAndConfigurationSettingsImpl template, String elementType) throws WriteExternalException {
    final Element configurationElement = new Element(elementType);
    parentNode.addContent(configurationElement);
    storeConfiguration(configurationElement, template);
  }

  private void storeConfiguration(final Element element, final RunnerAndConfigurationSettingsImpl configuration)
    throws WriteExternalException {
    final ConfigurationType type = configuration.getType();
    final boolean isSelected = mySelectedConfigurations.get(type) == configuration;
    element.setAttribute(SELECTED_ATTR, Boolean.valueOf(isSelected).toString());
    configuration.writeExternal(element);
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    myConfigurations.clear();

    final List children = parentNode.getChildren();
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      final Element element = (Element)iterator.next();
      final String elementName = element.getName();
      if (ACTIVE_TYPE.equals(elementName)) {
        final String typeName = element.getAttributeValue(NAME_ATTR);
        if (typeName != null) myActiveConfigurationType = myTypesByName.get(typeName);
      }
      else {
        loadConfiguration(element);
      }
    }
  }

  private void loadConfiguration(final Element element) throws InvalidDataException {
    RunnerAndConfigurationSettingsImpl configuration = new RunnerAndConfigurationSettingsImpl(this);
    configuration.readExternal(element);
    ConfigurationFactory factory = configuration.getFactory();
    if (factory == null) return;

    if (configuration.isTemplate()) {
      myTemplateConfigurationsMap.put(factory, configuration);
    }
    else {
      if (Boolean.valueOf(element.getAttributeValue(SELECTED_ATTR))) {
        mySelectedConfigurations.put(factory.getType(), configuration);
      }
      if (TEMP_CONFIGURATION.equals(element.getName())) {
        myTempConfiguration = configuration;
      }
      addConfiguration(configuration);
    }
  }


  public ConfigurationFactory getFactory(final String typeName, String factoryName) {
    final ConfigurationType type = myTypesByName.get(typeName);
    if (factoryName == null) {
      factoryName = type != null ? type.getConfigurationFactories()[0].getName() : null;
    }
    final ConfigurationFactory factory = findFactoryOfTypeNameByName(typeName, factoryName);
    return factory;
  }


  private ConfigurationFactory findFactoryOfTypeNameByName(final String typeName, final String factoryName) {
    return findFactoryOfTypeByName(myTypesByName.get(typeName), factoryName);
  }

  private ConfigurationFactory findFactoryOfTypeByName(final ConfigurationType type, final String factoryName) {
    if (type == null || factoryName == null) return null;
    final ConfigurationFactory[] factories = type.getConfigurationFactories();
    for (int i = 0; i < factories.length; i++) {
      final ConfigurationFactory factory = factories[i];
      if (factoryName.equals(factory.getName())) return factory;
    }
    return null;
  }

  public String getComponentName() {
    return "RunManager";
  }

  public void setTemporaryConfiguration(final RunnerAndConfigurationSettingsImpl tempConfiguration) {
    LOG.assertTrue(tempConfiguration != null);
    myConfigurations = getStableConfigurations();
    myTempConfiguration = tempConfiguration;
    addConfiguration(myTempConfiguration);
    setActiveConfiguration(myTempConfiguration);
  }

  public void setActiveConfiguration(final RunnerAndConfigurationSettingsImpl configuration) {
    final ConfigurationType type = configuration.getType();
    setActiveConfigurationFactory(type);
    setSelectedConfiguration(configuration);
  }

  private List<RunnerAndConfigurationSettingsImpl> getStableConfigurations() {
    final List<RunnerAndConfigurationSettingsImpl> result = new ArrayList<RunnerAndConfigurationSettingsImpl>(myConfigurations);
    result.remove(myTempConfiguration);
    return result;
  }

  public boolean isTemporary(final RunConfiguration configuration) {
    if (myTempConfiguration == null) return false;
    return myTempConfiguration.getConfiguration() == configuration;
  }

  public boolean isTemporary(RunnerAndConfigurationSettingsImpl settings) {
    return settings.equals(myTempConfiguration);
  }

  public RunConfiguration getTempConfiguration() {
    return myTempConfiguration == null ? null : myTempConfiguration.getConfiguration();
  }

  public void makeStable(final RunConfiguration configuration) {
    if (isTemporary(configuration)) {
      myTempConfiguration = null;
    }
  }

  public RunnerAndConfigurationSettings getSelectedConfiguration() {
    ConfigurationType activeFactory = getActiveConfigurationFactory();
    return activeFactory != null ? getSelectedConfiguration(activeFactory) : null;
  }

  public RunnerAndConfigurationSettings createRunConfiguration(String name, ConfigurationFactory type) {
    return createConfiguration(name, type);
  }


  private class MyRefactoringElementListenerProvider implements RefactoringElementListenerProvider {
    public RefactoringElementListener getListener(final PsiElement element) {
      RefactoringElementListenerComposite composite = null;
      for (int i = 0; i < myConfigurations.size(); i++) {
        final RunConfiguration configuration = myConfigurations.get(i).getConfiguration();
        if (configuration instanceof RuntimeConfiguration) { // todo: perhaps better way to handle listeners?
          final RefactoringElementListener listener = ((RuntimeConfiguration)configuration).getRefactoringElementListener(element);
          if (listener != null) {
            if (composite == null) {
              composite = new RefactoringElementListenerComposite();
            }
            composite.addListener(listener);
          }
        }
      }
      return composite;
    }
  }

  public static RunManagerImpl getInstanceImpl(final Project project) {
    return (RunManagerImpl)getInstance(project);
  }
}
