package com.intellij.execution.actions;

import com.intellij.execution.ConfigurationTypeEx;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.application.ApplicationConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.execution.junit.JUnitConfigurationProducer;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

class PreferedProducerFind {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.PreferedProducerFind");

  public RunnerAndConfigurationSettings createConfiguration(final Location location, final ConfigurationContext context) {
    LOG.assertTrue(location != null);
    final RuntimeConfigurationProducer preferedProducer = findPreferedProducer(location, context);
    if (preferedProducer != null) {
      return preferedProducer.getConfiguration();
    }
    final ConfigurationType[] factories = RunManager.getInstance(location.getProject()).getConfigurationFactories();
    for(int i = 0; i < factories.length; i++){
      final ConfigurationType type = factories[i];
      if (type instanceof ConfigurationTypeEx) {
        final RunnerAndConfigurationSettings configuration = ((ConfigurationTypeEx)type).createConfigurationByLocation(location);
        if (configuration != null) {
          return configuration;
        }
      }
    }
    return null;
  }

  public RuntimeConfigurationProducer findPreferedProducer(final Location location, final ConfigurationContext context) {
    final ArrayList<RuntimeConfigurationProducer> prototypes = new ArrayList<RuntimeConfigurationProducer>();
    prototypes.addAll(Arrays.asList(JUnitConfigurationProducer.PROTOTYPES));
    prototypes.add(ApplicationConfigurationProducer.PROTOTYPE);
    final ArrayList<RuntimeConfigurationProducer> producers = new ArrayList<RuntimeConfigurationProducer>();
    for (Iterator<RuntimeConfigurationProducer> iterator = prototypes.iterator(); iterator.hasNext();) {
      final RuntimeConfigurationProducer prototype = iterator.next();
      final RuntimeConfigurationProducer producer = prototype.createProducer(location, context);
      if (producer.getConfiguration() != null) {
        LOG.assertTrue(producer.getSourceElement() != null, producer.toString());
        producers.add(producer);
      }
    }
    if (producers.size() == 0) return null;
    Collections.sort(producers, RuntimeConfigurationProducer.COMPARATOR);
    return producers.get(0);
  }
}
