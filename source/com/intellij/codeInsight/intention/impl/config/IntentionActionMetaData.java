/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import java.net.URL;


public final class IntentionActionMetaData {
  public final String myFamily;
  public final URL[] myExampleUsagesBefore;
  public final URL[] myExampleUsagesAfter;
  public final URL myDescription;
  public final String[] myCategory;

  public IntentionActionMetaData(String family,
                                 URL[] exampleUsagesBefore,
                                 URL[] exampleUsagesAfter,
                                 URL description,
                                 String[] category) {
    myFamily = family;
    myExampleUsagesBefore = exampleUsagesBefore;
    myExampleUsagesAfter = exampleUsagesAfter;
    myDescription = description;
    myCategory = category;
  }
}