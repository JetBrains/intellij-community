package com.intellij.featureStatistics;

import com.intellij.featureStatistics.ui.ProgressTipPanel;
import com.intellij.ide.TipOfTheDayManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressFunComponentProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class FeatureUsageTracker implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.featureStatistics.FeatureUsageTracker");

  private static final long DAY = 1000 * 60 * 60 * 24;
  private long FIRST_RUN_TIME = 0;
  private boolean HAVE_BEEN_SHOWN = false;

  public boolean SHOW_IN_COMPILATION_PROGRESS = true;
  public boolean SHOW_IN_OTHER_PROGRESS = true;
  private ProductivityFeaturesRegistry myRegistry;

  public static FeatureUsageTracker getInstance() {
    return ApplicationManager.getApplication().getComponent(FeatureUsageTracker.class);
  }

  public FeatureUsageTracker(ProgressManager progressManager, ProductivityFeaturesRegistry productivityFeaturesRegistry) {
    myRegistry = productivityFeaturesRegistry;
    progressManager.registerFunComponentProvider(new ProgressFunProvider());
  }

  public String getComponentName() {
    return "FeatureUsageStatistics";
  }

  public void initComponent() { }

  private String[] getFeaturesToShow(Project project) {
    List<String> result = new ArrayList<String>();
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    Set<String> ids = registry.getFeatureIds();
    for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
      String id = iterator.next();
      if (isToBeShown(id, project)) {
        result.add(id);
      }
    }
    return result.toArray(new String[result.size()]);
  }

  private boolean isToBeShown(String featureId, Project project) {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
    if (!descriptor.isUnused()) return false;

    String[] dependencyFeatures = descriptor.getDependencyFeatures();
    boolean locked = dependencyFeatures.length > 0;
    for (int i = 0; locked && i < dependencyFeatures.length; i++) {
      if (!registry.getFeatureDescriptor(dependencyFeatures[i]).isUnused()) {
        locked = false;
      }
    }
    if (locked) return false;

    ApplicabilityFilter[] filters = registry.getMatchingFilters(featureId);
    for (int i = 0; i < filters.length; i++) {
      ApplicabilityFilter filter = filters[i];
      if (!filter.isApplicable(featureId, project)) return false;
    }

    long current = System.currentTimeMillis();
    long succesive_interval = descriptor.getDaysBetweenSuccesiveShowUps() * DAY + descriptor.getShownCount() * 2;
    long firstShowUpInterval = descriptor.getDaysBeforeFirstShowUp() * DAY;
    long lastTimeUsed = descriptor.getLastTimeUsed();
    long lastTimeShown = descriptor.getLastTimeShown();
    return lastTimeShown == 0 && firstShowUpInterval + getFirstRunTime() < current ||
           lastTimeShown > 0 && current - lastTimeShown > succesive_interval && current - lastTimeUsed > succesive_interval;
  }

  public long getFirstRunTime() {
    if (FIRST_RUN_TIME == 0) {
      FIRST_RUN_TIME = System.currentTimeMillis();
    }
    return FIRST_RUN_TIME;
  }

  public void disposeComponent() {
  }

  public String getExternalFileName() {
    return "feature.usage.statistics";
  }

  public void readExternal(Element element) throws InvalidDataException {
    List featuresList = element.getChildren("feature");
    for (int i = 0; i < featuresList.size(); i++) {
      Element featureElement = (Element)featuresList.get(i);
      FeatureDescriptor descriptor = myRegistry.getFeatureDescriptor(featureElement.getAttributeValue("id"));
      if (descriptor != null) {
        descriptor.readStatistics(featureElement);
      }
    }

    try {
      FIRST_RUN_TIME = Long.parseLong(element.getAttributeValue("first-run"));
    }
    catch (NumberFormatException e) {
      FIRST_RUN_TIME = 0;
    }

    HAVE_BEEN_SHOWN = Boolean.valueOf(element.getAttributeValue("have-been-shown")).booleanValue();
    SHOW_IN_OTHER_PROGRESS = Boolean.valueOf(element.getAttributeValue("show-in-other", "true")).booleanValue();
    SHOW_IN_COMPILATION_PROGRESS = Boolean.valueOf(element.getAttributeValue("show-in-compilation", "true")).booleanValue();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    Set<String> ids = registry.getFeatureIds();
    for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
      String id = iterator.next();
      Element featureElement = new Element("feature");
      featureElement.setAttribute("id", id);
      FeatureDescriptor descriptor = registry.getFeatureDescriptor(id);
      descriptor.writeStatistics(featureElement);
      element.addContent(featureElement);
    }

    element.setAttribute("first-run", String.valueOf(getFirstRunTime()));
    element.setAttribute("have-been-shown", String.valueOf(HAVE_BEEN_SHOWN));
    element.setAttribute("show-in-other", String.valueOf(SHOW_IN_OTHER_PROGRESS));
    element.setAttribute("show-in-compilation", String.valueOf(SHOW_IN_COMPILATION_PROGRESS));
  }

  public void triggerFeatureUsed(String featureId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    ProductivityFeaturesRegistry registry = ProductivityFeaturesRegistry.getInstance();
    FeatureDescriptor descriptor = registry.getFeatureDescriptor(featureId);
    if (descriptor == null) {
     // TODO: LOG.error("Feature '" + featureId +"' must be registered prior triggerFeatureUsed() is called");
    }
    else {
      descriptor.triggerUsed();
    }
  }

  public void triggerFeatureShown(String featureId) {
    FeatureDescriptor descriptor = ProductivityFeaturesRegistry.getInstance().getFeatureDescriptor(featureId);
    if (descriptor != null) {
      descriptor.triggerShown();
    }
  }

  private final class ProgressFunProvider implements ProgressFunComponentProvider {
    public JComponent getProgressFunComponent(Project project, String processId) {
      if ("compilation".equals(processId)) {
        if (!SHOW_IN_COMPILATION_PROGRESS) return null;
      }
      else {
        if (!SHOW_IN_OTHER_PROGRESS) return null;
      }

      String[] features = getFeaturesToShow(project);
      if (features.length > 0) {
        if (!HAVE_BEEN_SHOWN) {
          HAVE_BEEN_SHOWN = true;
          String[] newFeatures = new String[features.length + 1];
          newFeatures[0] = ProductivityFeaturesRegistry.WELCOME;
          System.arraycopy(features, 0, newFeatures, 1, features.length);
          features = newFeatures;
        }
        TipOfTheDayManager.getInstance().doNotShowThisTime();
        return new ProgressTipPanel(features, project).getComponent();
      }
      return null;
    }
  }
}