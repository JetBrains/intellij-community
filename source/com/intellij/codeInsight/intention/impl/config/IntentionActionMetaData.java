/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;


public final class IntentionActionMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionActionMetaData");

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
    checkURLCase(description);
    for (URL url : exampleUsagesBefore) {
      checkURLCase(url);
    }
    for (URL url : exampleUsagesAfter) {
      checkURLCase(url);
    }
    myFamily = family;
    myExampleUsagesBefore = exampleUsagesBefore;
    myExampleUsagesAfter = exampleUsagesAfter;
    myDescription = description;
    myCategory = category;
  }

  private static void checkURLCase(final URL url) {
    try {
      String canonicalPath = new File(url.getPath()).getCanonicalFile().getPath();
      String path = new File(url.getPath()).getPath();
      LOG.assertTrue(path.equals(canonicalPath), "Intention description path not found. Expected:\n"+path+"\nBut was:\n"+canonicalPath);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}