package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.Version;

public class LombokExternalLibraryResolverTest extends AbstractLombokLightCodeInsightTestCase {

  public void testResolveClass() {
    LombokExternalLibraryResolver cut = new LombokExternalLibraryResolver();
    ExternalLibraryResolver.ExternalClassResolveResult result = cut.resolveClass("Data", ThreeState.YES, getModule());

    assertNotNull(result);
    assertEquals(LombokClassNames.DATA, result.getQualifiedClassName());
    checkLombokLibraryDescriptor(result.getLibraryDescriptor());
  }

  public void testResolvePackage() {
    LombokExternalLibraryResolver cut = new LombokExternalLibraryResolver();
    ExternalLibraryDescriptor result = cut.resolvePackage("lombok");
    assertNotNull(result);
    checkLombokLibraryDescriptor(result);
  }

  private static void checkLombokLibraryDescriptor(ExternalLibraryDescriptor result) {
    assertEquals("org.projectlombok", result.getLibraryGroupId());
    assertEquals("lombok", result.getLibraryArtifactId());
    assertEquals(Version.LAST_LOMBOK_VERSION, result.getPreferredVersion());
    assertEquals(DependencyScope.PROVIDED, result.getPreferredScope());
  }
}
