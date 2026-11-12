package de.plushnikov.intellij.plugin.resolver;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThreeState;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import de.plushnikov.intellij.plugin.Version;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LombokExternalLibraryResolverTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, LombokExternalLibraryResolverTest::descriptor);

  private static @NotNull LightProjectDescriptor descriptor(@NotNull String testName) {
    if (testName.endsWith("WithoutLombok")) return LombokTestUtil.WITHOUT_LOMBOK_JAVA_21_DESCRIPTOR;
    if (testName.endsWith("WithOldLombok")) return LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR;
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  @Test
  public void testResolveClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LombokExternalLibraryResolver cut = new LombokExternalLibraryResolver();
    ExternalLibraryResolver.ExternalClassResolveResult result = cut.resolveClass("Data", ThreeState.YES, myFixture.getModule());

    assertNotNull(result);
    assertEquals(LombokClassNames.DATA, result.getQualifiedClassName());
    checkLombokLibraryDescriptor(result.getLibraryDescriptor());
  }

  @Test
  public void testResolveSuperBuilderWithoutLombok() {
    checkSuperBuilder(LombokClassNames.SUPER_BUILDER);
  }

  @Test
  public void testResolveSuperBuilderWithLombok() {
    checkSuperBuilder(LombokClassNames.SUPER_BUILDER);
  }

  /**
   * Lombok before 1.18.48 has only {@code lombok.experimental.SuperBuilder}. The resolver must not offer to add Lombok again.
   */
  @Test
  public void testResolveSuperBuilderWithOldLombok() {
    checkSuperBuilder(LombokClassNames.EXPERIMENTAL_SUPER_BUILDER);
  }

  private void checkSuperBuilder(String expectedName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExternalLibraryResolver.ExternalClassResolveResult result =
      ReadAction.computeBlocking(() -> new LombokExternalLibraryResolver().resolveClass("SuperBuilder", ThreeState.YES, myFixture.getModule()));
    assertNotNull(result);
    assertEquals(expectedName, result.getQualifiedClassName());
  }

  @Test
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
