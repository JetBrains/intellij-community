package de.plushnikov.intellij.plugin.action.intellij;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import org.jetbrains.annotations.NotNull;

public class FieldMayBeFinalInspectionTest extends LightInspectionTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/inspection/canBeFinalInspection";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package lombok;\npublic @interface Setter { }");
    myFixture.addClass("package lombok;\npublic @interface Getter { }");
    myFixture.addClass("package lombok;\npublic @interface Data { }");
    myFixture.addClass("package lombok;\npublic @interface Value { }");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public Sdk getSdk() {
        return JavaSdk.getInstance().createJdk("java 1.7", "lib/mockJDK-1.7", false);
      }

      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_7);
      }
    };
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeFinalInspection();
  }

  public void testClassNormal() throws Exception {
    doTest();
  }

  public void testClassWithData() throws Exception {
    doTest();
  }

  public void testClassWithFieldSetter() throws Exception {
    doTest();
  }

  public void testClassWithGetter() throws Exception {
    doTest();
  }

  public void testClassWithSetter() throws Exception {
    doTest();
  }

  public void testClassWithValue() throws Exception {
    doTest();
  }

}
