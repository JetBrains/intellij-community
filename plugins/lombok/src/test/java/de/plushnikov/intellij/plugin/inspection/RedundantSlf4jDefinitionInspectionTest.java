package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

public class RedundantSlf4jDefinitionInspectionTest extends LombokInspectionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/" + TEST_DATA_INSPECTION_DIRECTORY + "/redundantSlf4jDeclaration";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {

      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        MavenDependencyUtil.addFromMaven(model, "org.projectlombok:lombok:1.18.12");
        MavenDependencyUtil.addFromMaven(model, "org.slf4j:slf4j-log4j12:1.7.30");
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
      }
    };
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantSlf4jDefinitionInspection();
  }

  public void testRedundantSlf4jDefinition() {
    doTest();
    checkQuickFix("Annotate class 'RedundantSlf4jDefinition' as @Slf4j");
  }

}
