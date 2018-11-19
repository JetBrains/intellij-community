// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import junit.framework.Test;
import junit.framework.TestSuite;

@SuppressWarnings({"JUnitTestClassNamingConvention"})
public class YamlJsonSchemaTestSuite {
  public static Test suite() {
    final TestSuite suite = new TestSuite(YamlJsonSchemaTestSuite.class.getSimpleName());
    suite.addTestSuite(YamlByJsonSchemaDocumentationTest.class);
    suite.addTestSuite(YamlByJsonSchemaCompletionTest.class);
    suite.addTestSuite(YamlByJsonSchemaHeavyCompletionTest.class);
    suite.addTestSuite(YamlByJsonSchemaHighlightingTest.class);
    suite.addTestSuite(YamlByJsonSchemaQuickFixTest.class);
    return suite;
  }
}
