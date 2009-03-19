package com.intellij.featureStatistics;

import com.intellij.codeInsight.completion.XmlCompletionContributor;
import com.intellij.xml.XmlBundle;

import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class XmlProductivityFeatureProvider extends ProductivityFeaturesProvider {
  public FeatureDescriptor[] getFeatureDescriptors() {
    return new FeatureDescriptor[] { new FeatureDescriptor(XmlCompletionContributor.TAG_NAME_COMPLETION_FEATURE,
                                                           "completion",
                                                           "TagNameCompletion.html",
                                                           XmlBundle.message("tag.name.completion.display.name"),
                                                           0,
                                                           1,
                                                           Collections.<String>emptySet(),
                                                           3,
                                                           this)};
  }

  public GroupDescriptor[] getGroupDescriptors() {
    return new GroupDescriptor[0];
  }

  public ApplicabilityFilter[] getApplicabilityFilters() {
    return new ApplicabilityFilter[0];
  }
}
