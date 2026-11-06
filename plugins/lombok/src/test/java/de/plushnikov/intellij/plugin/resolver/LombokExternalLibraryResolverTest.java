package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.Version;
import org.jetbrains.annotations.NotNull;

public class LombokExternalLibraryResolverTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    String testName = getTestName(false);
    if (testName.endsWith("WithoutLombok")) return LombokTestUtil.WITHOUT_LOMBOK_JAVA_21_DESCRIPTOR;
    if (testName.endsWith("WithOldLombok")) return LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR;
    return super.getProjectDescriptor();
  }

  public void testResolveClass() {
    LombokExternalLibraryResolver cut = new LombokExternalLibraryResolver();
    ExternalLibraryResolver.ExternalClassResolveResult result = cut.resolveClass("Data", ThreeState.YES, getModule());

    assertNotNull(result);
    assertEquals(LombokClassNames.DATA, result.getQualifiedClassName());
    checkLombokLibraryDescriptor(result.getLibraryDescriptor());
  }

  public void testResolveSuperBuilderWithoutLombok() {
    checkSuperBuilder(LombokClassNames.SUPER_BUILDER);
  }

  public void testResolveSuperBuilderWithLombok() {
    checkSuperBuilder(LombokClassNames.SUPER_BUILDER);
  }

  /**
   * Lombok before 1.18.48 has only {@code lombok.experimental.SuperBuilder}. The resolver must not offer to add Lombok again.
   */
  public void testResolveSuperBuilderWithOldLombok() {
    checkSuperBuilder(LombokClassNames.EXPERIMENTAL_SUPER_BUILDER);
  }

  private void checkSuperBuilder(String expectedName) {
    ExternalLibraryResolver.ExternalClassResolveResult result =
      new LombokExternalLibraryResolver().resolveClass("SuperBuilder", ThreeState.YES, getModule());
    assertNotNull(result);
    assertEquals(expectedName, result.getQualifiedClassName());
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
