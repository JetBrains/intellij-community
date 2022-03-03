package de.plushnikov.intellij.plugin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

public class LombokTestUtil {

  public static final String LOMBOK_MAVEN_COORDINATES = "org.projectlombok:lombok:" + Version.LAST_LOMBOK_VERSION;

  public static final DefaultLightProjectDescriptor LOMBOK_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      MavenDependencyUtil.addFromMaven(model, LOMBOK_MAVEN_COORDINATES);
      MavenDependencyUtil.addFromMaven(model, "com.google.guava:guava:27.0.1-jre");
      MavenDependencyUtil.addFromMaven(model, "org.slf4j:slf4j-api:1.7.30");
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }

    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }
  };

  public static final DefaultLightProjectDescriptor LOMBOK_NEW_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      MavenDependencyUtil.addFromMaven(model, LOMBOK_MAVEN_COORDINATES);
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.HIGHEST);
    }
  };



  public static final DefaultLightProjectDescriptor LOMBOK_OLD_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      MavenDependencyUtil.addFromMaven(model, "org.projectlombok:lombok:1.18.2");
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }
  };
}
