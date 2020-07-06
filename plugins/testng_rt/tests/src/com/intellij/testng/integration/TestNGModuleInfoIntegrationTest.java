package com.intellij.testng.integration;

import com.intellij.execution.ExecutionException;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.configuration.AbstractTestNGPackageConfigurationProducer;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;
import jetbrains.buildServer.messages.serviceMessages.TestStarted;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import static com.intellij.execution.testframework.TestSearchScope.SINGLE_MODULE;

public class TestNGModuleInfoIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Override
  protected String getTestContentRoot() {
    return VfsUtilCore
      .pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/testng_rt/tests/testData/integration/forkProjectWithModuleInfoTestNG");
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    final ArtifactRepositoryManager repoManager = getRepoManager();

    ModuleRootModificationUtil.updateModel(myModule, model -> {
      String contentUrl = getTestContentRoot();
      ContentEntry contentEntry = model.addContentEntry(contentUrl);
      String contentUrlSrc = contentUrl + "/source";
      contentEntry.addSourceFolder(contentUrlSrc, false);
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_11);
    });

    JpsMavenRepositoryLibraryDescriptor testNGLib =
      new JpsMavenRepositoryLibraryDescriptor("org.testng", "testng", "6.8");
    addMavenLibs(myModule, testNGLib, repoManager);
  }

  public void testModuleInfoInSourceRoot() throws ExecutionException {
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("p").getParentPackage();
    final MapDataContext dataContext = new MapDataContext();
    dataContext.put("myModule", myModule);
    TestNGConfiguration configuration =
      createTestNGConfiguration(defaultPackage, AbstractTestNGPackageConfigurationProducer.class, dataContext);
    configuration.setUseModulePath(true);
    configuration.setWorkingDirectory("$MODULE_WORKING_DIR$");
    configuration.setSearchScope(SINGLE_MODULE);
    TestData dataTNG = configuration.getPersistantData();
    dataTNG.TEST_OBJECT = new String(TestType.PACKAGE.getType());
    final ProcessOutput output = doStartTestsProcess(configuration);
    assertTrue(output.sys.toString().contains("testng"));
    assertSize(2, ContainerUtil.filter(output.messages, TestStarted.class::isInstance));
    assertEmpty(output.err);
  }
}

