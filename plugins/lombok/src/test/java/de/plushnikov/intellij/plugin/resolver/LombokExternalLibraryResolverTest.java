package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokClassNames;

public class LombokExternalLibraryResolverTest extends AbstractLombokLightCodeInsightTestCase {

  public void testResolverConstruction() {
    LombokExternalLibraryResolver cut = new LombokExternalLibraryResolver();
    ExternalLibraryResolver.ExternalClassResolveResult result = cut.resolveClass("Data",
                                                                                        ThreeState.YES,
                                                                                        getModule());
    assertNotNull(result);
    assertEquals(LombokClassNames.DATA, result.getQualifiedClassName());
  }
}
