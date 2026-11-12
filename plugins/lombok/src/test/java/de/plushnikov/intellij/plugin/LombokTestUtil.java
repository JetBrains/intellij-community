package de.plushnikov.intellij.plugin;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

public final class LombokTestUtil {

  public static final String LOMBOK_MAVEN_COORDINATES = "org.projectlombok:lombok:" + Version.LAST_LOMBOK_VERSION;

  private static final String JACKSON_ARTIFACT = "com.fasterxml.jackson.core:jackson-databind:2.19.1";
  private static final String SLF4J_ARTIFACT = "org.slf4j:slf4j-api:2.0.17";
  private static final String GUAVA_ARTIFACT = "com.google.guava:guava:33.4.8-jre";
  private static final String JSR305_ARTIFACT = "com.google.code.findbugs:jsr305:3.0.2";

  public static @NotNull String getTestDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//plugins/lombok:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString();
    }
    return PathManager.getCommunityHomePath() + "/plugins/lombok/testData";
  }

  public static void addLombokDependency(@NotNull ModifiableRootModel model) {
    MavenDependencyUtil.addFromMaven(model, LOMBOK_MAVEN_COORDINATES, false, DependencyScope.PROVIDED);
  }
  public static final DefaultLightProjectDescriptor LOMBOK_JAVA_1_8_DESCRIPTOR = new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk18) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      DefaultLightProjectDescriptor.addJetBrainsAnnotationsWithTypeUse(model);
      addLombokDependency(model);
      MavenDependencyUtil.addFromMaven(model, JACKSON_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, JSR305_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, GUAVA_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, SLF4J_ARTIFACT);
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }
  };

  public static final DefaultLightProjectDescriptor WITHOUT_LOMBOK_JAVA_21_DESCRIPTOR = new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      DefaultLightProjectDescriptor.addJetBrainsAnnotationsWithTypeUse(model);
      MavenDependencyUtil.addFromMaven(model, JACKSON_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, JSR305_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, GUAVA_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, SLF4J_ARTIFACT);
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.HIGHEST);
    }
  };

  public static final DefaultLightProjectDescriptor LOMBOK_JAVA21_DESCRIPTOR = new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      DefaultLightProjectDescriptor.addJetBrainsAnnotationsWithTypeUse(model);
      addLombokDependency(model);
      MavenDependencyUtil.addFromMaven(model, JACKSON_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, JSR305_ARTIFACT);
      //MavenDependencyUtil.addFromMaven(model, GUAVA_ARTIFACT);
      MavenDependencyUtil.addFromMaven(model, SLF4J_ARTIFACT);
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.HIGHEST);
    }
  };

  public static final DefaultLightProjectDescriptor LOMBOK_OLD_JAVA_1_8_DESCRIPTOR = new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk18) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      MavenDependencyUtil.addFromMaven(model, "org.projectlombok:lombok:1.18.2", false, DependencyScope.PROVIDED);
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }
  };

  /**
   * Copies {@code fileName} of the test data to the same path in the project, configures it in the editor, and returns its PSI file.
   */
  public static PsiFile loadToPsiFile(@NotNull JavaCodeInsightTestFixture fixture, @NotNull String fileName) {
    VirtualFile virtualFile = fixture.copyFileToProject(fileName, fileName);
    fixture.configureFromExistingVirtualFile(virtualFile);
    return fixture.getFile();
  }

  /**
   * Creates the inspection fixture of a Lombok test. The test data path is {@code basePath} in the Lombok test data.
   */
  public static @NotNull TestFixture<LightJavaInspectionFixture> lombokInspectionFixture(@NotNull TestFixture<LightJavaProject> projectFixture,
                                                                                       @NotNull Supplier<? extends InspectionProfileEntry> inspection,
                                                                                       @NotNull String basePath) {
    return lightJavaInspectionFixture(projectFixture, inspection, ArrayUtilRt.EMPTY_STRING_ARRAY, null, null, getTestDataPath() + basePath);
  }

  /**
   * Configures the file `<test name>.java` with {@code text} and checks the highlighting.
   * Each {@code %s} in {@code text} becomes the test name.
   */
  public static void configureAndTest(@NotNull LightJavaInspectionFixture inspection, String text) {
    String className = inspection.getTestName(false);
    String fileName = className + ".java";

    JavaCodeInsightTestFixture fixture = inspection.getFixture();
    fixture.configureByText(fileName, text.formatted(className));
    fixture.checkHighlighting();
  }
}
