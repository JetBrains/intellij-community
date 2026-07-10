package com.intellij.lang.properties.codeInspection;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.RemovePropertyFromBundleFix;
import com.intellij.lang.properties.codeInspection.unused.UnusedPropertyInspection;
import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;

import java.io.IOException;
import java.util.List;

public class UnusedPropertyInspectionTest extends CodeInsightFixtureTestCase<ModuleFixtureBuilder<?>> {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("properties") + "/tests/testData/propertiesFile/unused";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    configureAdditionalModule();
    myFixture.enableInspections(UnusedPropertyInspection.class);
  }

  private void configureAdditionalModule() throws IOException {
    final String path = ProjectKt.getStateStore(getProject()).getProjectBasePath() + "/module";
    final ModuleManager moduleManager = ModuleManager.getInstance(getProject());

    final Module module = WriteAction.compute(() -> moduleManager.newModule(path, JavaModuleType.getModuleType().getId()));

    ModuleRootModificationUtil.addDependency(module, myModule);
    configureSources(module);
  }

  private void configureSources(Module module) throws IOException {
    final SourceFolder src = PsiTestUtil.addSourceRoot(module, myFixture.getTempDirFixture().findOrCreateDir("src"));
    @Language("JAVA") final String javaClass = """
      public class Main {
        static {
          System.getProperty("used");
        }
      }""";
    VfsTestUtil.createFile(src.getFile(), "org/main/Main.java", javaClass);
  }

  public void testUnused() {
    myFixture.configureByFile("root_project.properties");
    myFixture.checkHighlighting();
  }

  public void testRemovePropertyFromAllLocales() {
    PsiFile defaultFile = myFixture.addFileToProject("p.properties", "key=value\nother=kept");
    PsiFile enFile = myFixture.addFileToProject("p_en.properties", "key=en\nother=kept_en");
    PsiFile frFile = myFixture.addFileToProject("p_fr.properties", "key=fr\nother=kept_fr");
    myFixture.configureFromExistingVirtualFile(defaultFile.getVirtualFile());

    Property property = (Property)PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("key");
    assertNotNull(property);

    RemovePropertyFromBundleFix fix = new RemovePropertyFromBundleFix(property);
    ActionContext context = ActionContext.from(myFixture.getEditor(), defaultFile);
    ModCommand command = fix.asModCommandAction().perform(context);

    CommandProcessor.getInstance().executeCommand(getProject(), () ->
      ModCommandExecutor.getInstance().executeInteractively(context, command, myFixture.getEditor()), null, null);

    assertNull(PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("key"));
    assertNull(PropertiesImplUtil.getPropertiesFile(enFile).findPropertyByKey("key"));
    assertNull(PropertiesImplUtil.getPropertiesFile(frFile).findPropertyByKey("key"));

    assertNotNull(PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("other"));
    assertNotNull(PropertiesImplUtil.getPropertiesFile(enFile).findPropertyByKey("other"));
    assertNotNull(PropertiesImplUtil.getPropertiesFile(frFile).findPropertyByKey("other"));
  }

  public void testDuplicateUnusedPropertyHasSingleRemoveFix() {
    myFixture.configureByText("standalone.properties", "<caret>key=value1\nkey=value2\n");

    var removeFixes = myFixture.filterAvailableIntentions("Remove property");
    assertEquals("Expected exactly one 'Remove property' fix for duplicate+unused property", 1, removeFixes.size());
  }

  public void testRemoveDuplicatePropertyFromSingleFile() throws IOException {
    myFixture.addFileToProject("q.properties", "key=value1\nkey=value2\nother=kept\n");
    myFixture.addFileToProject("q_en.properties", "key=en\nother=kept_en\n");
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("q.properties"));

    var fix = myFixture.findSingleIntention("Remove property");
    myFixture.launchAction(fix);

    myFixture.checkResult("key=value2\nother=kept\n");
    // The other locale file should not be affected
    assertEquals("key=en\nother=kept_en\n",
                 VfsUtilCore.loadText(myFixture.findFileInTempDir("q_en.properties")));
  }

  public void testRemovePropertyFromAllLocalesWithEscapedKey() {
    PsiFile defaultFile = myFixture.addFileToProject("p.properties", "foo\\ bar=value\nother=kept");
    PsiFile enFile = myFixture.addFileToProject("p_en.properties", "foo\\ bar=en\nother=kept_en");
    PsiFile frFile = myFixture.addFileToProject("p_fr.properties", "foo\\ bar=fr\nother=kept_fr");
    myFixture.configureFromExistingVirtualFile(defaultFile.getVirtualFile());

    Property property = (Property)PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("foo bar");
    assertNotNull(property);

    RemovePropertyFromBundleFix fix = new RemovePropertyFromBundleFix(property);
    ActionContext context = ActionContext.from(myFixture.getEditor(), defaultFile);
    ModCommand command = fix.asModCommandAction().perform(context);

    CommandProcessor.getInstance().executeCommand(getProject(), () ->
      ModCommandExecutor.getInstance().executeInteractively(context, command, myFixture.getEditor()), null, null);

    assertNull(PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("foo bar"));
    assertNull(PropertiesImplUtil.getPropertiesFile(enFile).findPropertyByKey("foo bar"));
    assertNull(PropertiesImplUtil.getPropertiesFile(frFile).findPropertyByKey("foo bar"));

    assertNotNull(PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("other"));
    assertNotNull(PropertiesImplUtil.getPropertiesFile(enFile).findPropertyByKey("other"));
    assertNotNull(PropertiesImplUtil.getPropertiesFile(frFile).findPropertyByKey("other"));
  }

  public void testBatchFixRemovesUnusedFromAllBundleFiles() {
    PsiFile defaultFile = myFixture.addFileToProject("q.properties", "unused=value\nused=value_used\n");
    PsiFile enFile = myFixture.addFileToProject("q_en.properties", "unused=en\nused=used_en\n");
    PsiFile frFile = myFixture.addFileToProject("q_fr.properties", "unused=fr\nused=used_fr\n");
    myFixture.configureFromExistingVirtualFile(defaultFile.getVirtualFile());

    // Run inspection in batch mode (isOnTheFly=false).
    LocalInspectionToolWrapper wrapper = new LocalInspectionToolWrapper(new UnusedPropertyInspection());
    GlobalInspectionContext inspectionContext = InspectionManager.getInstance(getProject()).createNewGlobalContext();
    List<ProblemDescriptor> descriptors = InspectionEngine.runInspectionOnFile(defaultFile, wrapper, inspectionContext);
    ProblemDescriptor unusedDescriptor = ContainerUtil.find(descriptors,
                                                            d -> ((Property)d.getPsiElement().getParent()).getUnescapedKey()
                                                              .equals("unused"));
    assertNotNull(unusedDescriptor);

    QuickFix<?>[] fixes = unusedDescriptor.getFixes();
    assertEquals(1, fixes.length);

    ModCommandQuickFix fix = (ModCommandQuickFix)fixes[0];
    ActionContext actionContext = ActionContext.from(unusedDescriptor);
    ModCommand command = fix.perform(getProject(), unusedDescriptor);

    CommandProcessor.getInstance().executeCommand(getProject(), () ->
      ModCommandExecutor.getInstance().executeInteractively(actionContext, command, myFixture.getEditor()), null, null);

    assertNull(PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("unused"));
    assertNull(PropertiesImplUtil.getPropertiesFile(enFile).findPropertyByKey("unused"));
    assertNull(PropertiesImplUtil.getPropertiesFile(frFile).findPropertyByKey("unused"));

    assertNotNull(PropertiesImplUtil.getPropertiesFile(defaultFile).findPropertyByKey("used"));
    assertNotNull(PropertiesImplUtil.getPropertiesFile(enFile).findPropertyByKey("used"));
    assertNotNull(PropertiesImplUtil.getPropertiesFile(frFile).findPropertyByKey("used"));
  }

  public void testEditingValueKeepsPropertyUnused() {
    myFixture.configureByText("p.properties", "unused=value1\n");
    assertEquals(1, myFixture.doHighlighting().stream().filter(info -> "Unused property".equals(info.getDescription())).count());

    Document document = myFixture.getEditor().getDocument();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(document.getText().indexOf('=') + 1, "x"));
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    assertEquals(1, myFixture.doHighlighting().stream().filter(info -> "Unused property".equals(info.getDescription())).count());
  }

  public void testAddingReferenceMarksPropertyUsed() {
    myFixture.configureByText("p.properties", "unused=value\n");
    assertEquals(1, myFixture.doHighlighting().stream().filter(info -> "Unused property".equals(info.getDescription())).count());

    myFixture.addFileToProject("Ref.java", "class Ref { Object key = \"unused\"; }");

    assertEquals(0, myFixture.doHighlighting().stream().filter(info -> "Unused property".equals(info.getDescription())).count());
  }
}