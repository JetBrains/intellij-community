package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.mock.MockModule;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LombokExternalLibraryResolverTest {

  @Test
  public void testResolverConstruction() {
    LombokExternalLibraryResolver cut = new LombokExternalLibraryResolver();
    ExternalLibraryResolver.ExternalClassResolveResult result = cut.resolveClass("Data",
                                                                                        ThreeState.YES,
                                                                                        new MockModule(() -> {
                                                                                        }));
    assertNotNull(result);
    assertEquals(LombokClassNames.DATA, result.getQualifiedClassName());
  }
}
