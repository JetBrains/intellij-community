package de.plushnikov.intellij.plugin.refactoring;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDescriptor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptorImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LombokEncapsulateFieldsTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/refactoring";
  }

  public void testEncapsulateLombokFields() {
    doTest("EncapsulateLombokFields", "distanceFunction", "maxDistanceFunction", "qualityFunction", "uwbScoreFilter");
  }

  public void testDataIssueEvent() {
    doTest("DataIssueEvent", "dataIssueLevel", "whereIsItComingFrom", "exceptionNullable");
  }

  private void doTest(final String className, final String... fieldNames) {
    myFixture.configureByFile(getTestName(false) + ".java");
    PsiClass aClass = myFixture.findClass(className);
    assertNotNull("Tested class not found", aClass);

    doTest(aClass, ContainerUtil.map(fieldNames, name -> aClass.findFieldByName(name, false)));

    myFixture.checkResultByFile(getTestName(false) + "_after.java", true);
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
    LocalFileSystem.getInstance().refresh(false);
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
