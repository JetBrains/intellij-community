// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import junit.framework.Test;
import junit.framework.TestSuite;

@SuppressWarnings({"JUnitTestClassNamingConvention"})
public final class YamlJsonSchemaTestSuite {
  public static Test suite() {
    final TestSuite suite = new TestSuite(YamlJsonSchemaTestSuite.class.getSimpleName());
    suite.addTestSuite(YamlByJsonSchemaDocumentationTest.class);
    suite.addTestSuite(YamlByJsonSchemaCompletionTest.class);
    suite.addTestSuite(YamlByJsonSchemaNestedCompletionTest.class);
    suite.addTestSuite(YamlByJsonSchemaHeavyNestedCompletionTest.class);
    suite.addTestSuite(YamlByJsonSchemaHeavyCompletionTest.class);
    suite.addTestSuite(YamlByJsonSchemaHighlightingTest.class);
    suite.addTestSuite(YamlByJsonSchemaQuickFixTest.class);
    return suite;
  }
}
