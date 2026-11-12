package de.plushnikov.intellij.plugin.refactoring;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDescriptor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptorImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LombokEncapsulateFieldsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/refactoring");

  @Test
  public void testEncapsulateLombokFields() {
    doTest("EncapsulateLombokFields", "distanceFunction", "maxDistanceFunction", "qualityFunction", "uwbScoreFilter");
  }

  @Test
  public void testDataIssueEvent() {
    doTest("DataIssueEvent", "dataIssueLevel", "whereIsItComingFrom", "exceptionNullable");
  }

  private void doTest(final String className, final String... fieldNames) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass, "Tested class not found");

      doTest(aClass, ContainerUtil.map(fieldNames, name -> aClass.findFieldByName(name, false)));

      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java", true);
    });
  }

  private static void doTest(final PsiClass aClass, final Collection<PsiField> fields) {
    final Project project = aClass.getProject();
    EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, new EncapsulateFieldsDescriptor() {
      @Override
      public FieldDescriptor[] getSelectedFields() {
        final List<FieldDescriptor> descriptors = new ArrayList<>(fields.size());
        for (PsiField field : fields) {
          descriptors.add(new FieldDescriptorImpl(
            field,
            GenerateMembersUtil.suggestGetterName(field),
            GenerateMembersUtil.suggestSetterName(field),
            GenerateMembersUtil.generateGetterPrototype(field),
            GenerateMembersUtil.generateSetterPrototype(field)
          ));
        }
        return descriptors.toArray(FieldDescriptor[]::new);
      }

      @Override
      public boolean isToEncapsulateGet() {
        return true;
      }

      @Override
      public boolean isToEncapsulateSet() {
        return true;
      }

      @Override
      public boolean isToUseAccessorsWhenAccessible() {
        return true;
      }

      @Override
      public String getFieldsVisibility() {
        return null;
      }

      @Override
      public String getAccessorsVisibility() {
        return PsiModifier.PUBLIC;
      }

      @Override
      public int getJavadocPolicy() {
        return DocCommentPolicy.MOVE;
      }

      @Override
      public PsiClass getTargetClass() {
        return aClass;
      }
    });
    processor.run();
    StandardFileSystems.local().refresh(false);
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
