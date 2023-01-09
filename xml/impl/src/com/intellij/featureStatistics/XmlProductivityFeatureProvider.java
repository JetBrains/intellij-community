// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.codeInsight.completion.XmlCompletionContributor;
import com.intellij.xml.XmlBundle;

import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class XmlProductivityFeatureProvider extends ProductivityFeaturesProvider {
  @Override
  public FeatureDescriptor[] getFeatureDescriptors() {
    return new FeatureDescriptor[]{new FeatureDescriptor(XmlCompletionContributor.TAG_NAME_COMPLETION_FEATURE,
                                                         "completion",
                                                         "TagNameCompletion",
                                                         XmlBundle.message("tag.name.completion.display.name"),
                                                         0,
                                                         1,
                                                         Collections.emptySet(),
                                                         3,
                                                         this)};
  }
}
