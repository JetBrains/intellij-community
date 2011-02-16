/*
 * User: anna
 * Date: 30-Apr-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

public abstract class TypeMigrationTestBase extends MultiFileTestCase{
  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/typeMigration/testData";
  }

  protected void doTestFieldType(@NonNls String fieldName, PsiType fromType, PsiType toType) throws Exception {
    doTestFieldType(fieldName, "Test", fromType, toType);
  }

  protected void doTestFieldType(@NonNls final String fieldName, String className, final PsiType rootType, final PsiType migrationType) throws Exception {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public TypeMigrationRules provide() throws Exception {
        final TypeMigrationRules rules = new TypeMigrationRules(rootType);
        rules.setBoundScope(GlobalSearchScope.projectScope(getProject()));
        rules.setMigrationRootType(migrationType);
        return rules;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        return aClass.findFieldByName(fieldName, false);
      }
    };

    start(provider, className);
  }

  protected void doTestMethodType(@NonNls final String methodName, final PsiType rootType, final PsiType migrationType) throws Exception {
    doTestMethodType(methodName, "Test", rootType, migrationType);
  }

  protected void doTestMethodType(@NonNls final String methodName, @NonNls String className, final PsiType rootType, final PsiType migrationType) throws Exception {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public TypeMigrationRules provide() throws Exception {
        final TypeMigrationRules rules = new TypeMigrationRules(rootType);
        rules.setBoundScope(GlobalSearchScope.projectScope(getProject()));
        rules.setMigrationRootType(migrationType);
        return rules;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        return aClass.findMethodsByName(methodName, false)[0];
      }
    };

    start(provider, className);
  }

  protected void doTestFirstParamType(@NonNls final String methodName, final PsiType rootType, final PsiType migrationType) throws Exception {
    doTestFirstParamType(methodName, "Test", rootType, migrationType);
  }

  protected void doTestFirstParamType(@NonNls final String methodName, String className, final PsiType rootType, final PsiType migrationType) throws Exception {
    final RulesProvider provider = new RulesProvider() {
      @Override
      public TypeMigrationRules provide() throws Exception {
        final TypeMigrationRules rules = new TypeMigrationRules(rootType);
        rules.setBoundScope(GlobalSearchScope.projectScope(getProject()));
        rules.setMigrationRootType(migrationType);
        return rules;
      }

      @Override
      public PsiElement victims(PsiClass aClass) {
        return aClass.findMethodsByName(methodName, false)[0].getParameterList().getParameters()[0];
      }
    };

    start(provider, className);
  }

  public void start(final RulesProvider provider) throws Exception {
    start(provider, "Test");
  }

  public void start(final RulesProvider provider, final String className) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        TypeMigrationTestBase.this.performAction(className, rootDir.getName(), provider);
      }
    });
  }

  private void performAction(String className, String rootDir, RulesProvider provider) throws Exception {
    PsiClass aClass = myJavaFacade.findClass(className);

    assertNotNull("Class " + className + " not found", aClass);

    final TestTypeMigrationProcessor pr = new TestTypeMigrationProcessor(getProject(), provider.victims(aClass), provider.provide());

    final UsageInfo[] usages = pr.findUsages();
    final String itemRepr = pr.getLabeler().getMigrationReport();

    pr.performRefactoring(usages);

    String itemName = className + ".items";
    String patternName = getTestDataPath() + getTestRoot() + getTestName(true) + "/after/" + itemName;

    File patternFile = new File(patternName);

    if (!patternFile.exists()) {
      PrintWriter writer = new PrintWriter(new FileOutputStream(patternFile));
      writer.print(itemRepr);
      writer.close();

      System.out.println("Pattern not found, file " + patternName + " created.");

      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(patternFile);
    }

    File graFile = new File(FileUtil.getTempDirectory() + File.separator + rootDir + File.separator + itemName);

    PrintWriter writer = new PrintWriter(new FileOutputStream(graFile));

    writer.print(itemRepr);
    writer.close();

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(graFile);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  interface RulesProvider {
    TypeMigrationRules provide() throws Exception;

    PsiElement victims(PsiClass aClass);
  }

  private class TestTypeMigrationProcessor extends TypeMigrationProcessor {
    public TestTypeMigrationProcessor(final Project project, final PsiElement root, final TypeMigrationRules rules) {
      super(project, root, rules);
    }

    @NotNull
    @Override
    public UsageInfo[] findUsages() {
      return super.findUsages();
    }


    @Override
    public void performRefactoring(final UsageInfo[] usages) {
      super.performRefactoring(usages);
    }


  }
}