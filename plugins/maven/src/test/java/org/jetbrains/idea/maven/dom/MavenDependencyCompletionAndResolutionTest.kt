// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom

import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.MavenCustomRepositoryHelper
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.intentions.ChooseFileIntentionAction
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.IOException

class MavenDependencyCompletionAndResolutionTest : MavenDomWithIndicesTestCase() {
  override fun importProjectOnSetup(): Boolean {
    return true
  }

  @Test
  fun testGroupIdCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><caret></groupId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "junit", "jmock", "test")
  }

  @Test
  fun testArtifactIdCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "junit")
  }

  @Test
  fun testDoNotCompleteArtifactIdOnUnknownGroup() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>unknown</groupId>
                           <artifactId><caret></artifactId>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom)
  }

  @Test
  fun testVersionCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version><caret></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val variants = getCompletionVariants(myProjectPom)
    assertEquals(mutableListOf("4.0", "3.8.2", "3.8.1"), variants)
  }

  @Test
  fun testDoNotCompleteVersionIfNoGroupIdAndArtifactId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <version><caret></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom) // should not throw
  }

  @Test
  fun testAddingLocalProjectsIntoCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>project-group</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <groupId>project-group</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      """.trimIndent())

    val m = createModulePom("m2",
                            """
                                      <groupId>project-group</groupId>
                                      <artifactId>m2</artifactId>
                                      <version>2</version>
                                      """.trimIndent())

    importProjectAsync()

    createModulePom("m2", """
      <groupId>project-group</groupId>
      <artifactId>m2</artifactId>
      <version>2</version>
      <dependencies>
        <dependency>
          <groupId>project-group</groupId>
          <artifactId><caret></artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    assertCompletionVariants(m, LOOKUP_STRING, "project-group:project:1", "project-group:m1:1", "project-group:m2:2")
    assertCompletionVariants(m, RENDERING_TEXT, "project", "m1", "m2")
  }

  @Test
  fun testResolvingPropertiesForLocalProjectsInCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <properties>
                         <module1Name>module1</module1Name>
                       </properties>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    createModulePom("m1",
                    """
                      <groupId>${'$'}{pom.parent.groupId}</groupId>
                      <artifactId>${'$'}{module1Name}</artifactId>
                      <parent>
                        <groupId>test</groupId>
                        <artifactId>project</artifactId>
                        <version>1</version>
                      </parent>
                      """.trimIndent())

    val m = createModulePom("m2",
                            """
                                      <groupId>test</groupId>
                                      <artifactId>module2</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    importProjectAsync()
    assertModules("project", mn("project", "module1"), "module2")

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>module2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>module1</artifactId>
          <version><caret></version>
        </dependency>
      </dependencies>
      """.trimIndent())

    assertCompletionVariants(m, "1")

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>module2</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId>module1</artifactId>
          <version>1</version>
        </dependency>
      </dependencies>
      """.trimIndent())

    checkHighlighting(m)
  }

  @Test
  fun testChangingExistingProjects() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                        <module>m1</module>
                        <module>m2</module>
                       </modules>
                       """.trimIndent())

    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    createModulePom("m2",
                    """
                      <groupId>test</groupId>
                      <artifactId>m2</artifactId>
                      <version>1</version>
                      """.trimIndent())
    importProjectAsync()

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId><caret></artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    assertCompletionVariants(m1, LOOKUP_STRING, "test:project:1", "test:m1:1", "test:m2:1")
    assertCompletionVariants(m1, RENDERING_TEXT, "project", "m1", "m2")

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      """.trimIndent())

    createModulePom("m2", """
      <groupId>test</groupId>
      <artifactId>m2_new</artifactId>
      <version>1</version>
      """.trimIndent())

    importProjectAsync()

    createModulePom("m1", """
      <groupId>test</groupId>
      <artifactId>m1</artifactId>
      <version>1</version>
      <dependencies>
        <dependency>
          <groupId>test</groupId>
          <artifactId><caret></artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())

    assertCompletionVariants(m1, LOOKUP_STRING, "test:project:1", "test:m1:1", "test:m2_new:1")
    assertCompletionVariants(m1, RENDERING_TEXT, "project", "m1", "m2_new")
  }

  @Test
  fun testChangingExistingProjectsWithArtifactIdsRemoval() = runBlocking {
    val m = createModulePom("m1",
                            """
                                      <groupId>project-group</groupId>
                                      <artifactId>m1</artifactId>
                                      <version>1</version>
                                      """.trimIndent())

    configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>project-group</groupId>
                              <artifactId><caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    importProjectsWithErrors(myProjectPom, m)

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "m1")

    createModulePom("m1", "")
    importProjectsWithErrors(myProjectPom, m)

    configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>project-group</groupId>
                              <artifactId><caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    assertCompletionVariants(myProjectPom)
  }

  @Test
  fun testRemovingExistingProjects() = runBlocking {
    val m1 = createModulePom("m1",
                             """
                                             <groupId>project-group</groupId>
                                             <artifactId>m1</artifactId>
                                             <version>1</version>
                                             """.trimIndent())

    val m2 = createModulePom("m2",
                             """
                                             <groupId>project-group</groupId>
                                             <artifactId>m2</artifactId>
                                             <version>1</version>
                                             """.trimIndent())

    configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>project-group</groupId>
                              <artifactId><caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    importProjectsWithErrors(myProjectPom, m1, m2)

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "m1", "m2")
    assertCompletionVariantsInclude(myProjectPom, LOOKUP_STRING, "project-group:m1:1", "project-group:m2:1")

    WriteAction.runAndWait<IOException> { m1.delete(null) }

    configConfirmationForYesAnswer()

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "m2")
    assertCompletionVariantsInclude(myProjectPom, LOOKUP_STRING, "project-group:m2:1")
  }

  @Test
  fun testResolutionOutsideTheProject() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><caret>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertResolved(myProjectPom, findPsiFile(f))
  }

  @Test
  fun testResolutionParentPathOutsideTheProject() = runBlocking {
    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/org/example/1.0/example-1.0.pom")

    val relativePathUnixSeparator =
      FileUtil.getRelativePath(File(myProjectRoot.getPath()), File(filePath))!!.replace("\\\\".toRegex(), "/")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<parent>
  <groupId>org.example</groupId>
  <artifactId>example</artifactId>
  <version>1.0</version>
  <relativePath>
$relativePathUnixSeparator<caret></relativePath>
</parent>"""
    )

    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertResolved(myProjectPom, findPsiFile(f))
  }

  @Test
  fun testResolveManagedDependency() = runBlocking {
    configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>junit</groupId>
                                <artifactId>junit</artifactId>
                                <version>4.0</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>junit</groupId>
                              <artifactId>junit<caret></artifactId>
                            </dependency>
                          </dependencies>
                          """.trimIndent())
    importProjectAsync()

    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertResolved(myProjectPom, findPsiFile(f))
  }

  @Test
  fun testResolveLATESTDependency() = runBlocking {
    val helper = MavenCustomRepositoryHelper(myDir, "local1")
    val repoPath = helper.getTestDataPath("local1")
    repositoryPath = repoPath

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>[1,4.0]</version>
                      </dependency>
                    </dependencies>
                    """.trimIndent())

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit<caret></artifactId>
                           <version>[1,4.0]</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)

    assertResolved(myProjectPom, findPsiFile(f))
  }

  @Test
  fun testResolutionIsTypeBased() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><caret>junit</artifactId>
                           <version>4.0</version>
                           <type>pom</type>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val filePath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.pom")
    val f = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
    assertResolved(myProjectPom, findPsiFile(f))
  }

  @Test
  fun testResolutionInsideTheProject() = runBlocking {
    val m1 = createModulePom("m1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    val m2 = createModulePom("m2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>m2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    importProjects(myProjectPom, m1, m2)

    createModulePom("m1",
                    """
                      <groupId>test</groupId>
                      <artifactId>m1</artifactId>
                      <version>1</version>
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId><caret>m2</artifactId>
                          <version>1</version>
                        </dependency>
                      </dependencies>
                      """.trimIndent())

    assertResolved(m1, findPsiFile(m2))
  }

  @Test
  fun testResolvingSystemScopeDependencies() = runBlocking {
    val libPath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version><caret>xxx</version>
    <scope>system</scope>
    <systemPath>
$libPath</systemPath>
  </dependency>
</dependencies>
""")

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)))
    checkHighlighting()
  }

  @Test
  fun testHighlightInvalidSystemScopeDependencies() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><error  descr="Dependency 'xxx:xxx:xxx' not found">xxx</error></groupId>
                           <artifactId><error  descr="Dependency 'xxx:xxx:xxx' not found">xxx</error></artifactId>
                           <version><error  descr="Dependency 'xxx:xxx:xxx' not found">xxx</error></version>
                           <scope>system</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testDoNotHighlightValidSystemScopeDependencies() = runBlocking {
    val libPath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>xxx</version>
    <scope>system</scope>
    <systemPath>
$libPath</systemPath>
  </dependency>
</dependencies>
""")
    checkHighlighting()
  }

  @Test
  fun testResolvingSystemScopeDependenciesWithProperties() = runBlocking {
    val libPath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<properties>
  <depPath>
$libPath</depPath>
</properties>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version><caret>xxx</version>
    <scope>system</scope>
    <systemPath>${"$"}{depPath}</systemPath>
  </dependency>
</dependencies>
""")

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)))
    checkHighlighting()
  }

  @Test
  fun testCompletionSystemScopeDependenciesWithProperties() = runBlocking {
    val libPath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<properties>
  <depDir>
${File(libPath).getParent()}</depDir>
</properties>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>xxx</version>
    <scope>system</scope>
    <systemPath>${"$"}{depDir}/<caret></systemPath>
  </dependency>
</dependencies>
""")

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "junit-4.0.jar")
  }

  @Test
  fun testResolvingSystemScopeDependenciesFromSystemPath() = runBlocking {
    val libPath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar")

    createProjectPom("""<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<dependencies>
  <dependency>
    <groupId>xxx</groupId>
    <artifactId>xxx</artifactId>
    <version>xxx</version>
    <scope>system</scope>
    <systemPath>
$libPath<caret></systemPath>
  </dependency>
</dependencies>
""")

    assertResolved(myProjectPom, findPsiFile(LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)))
    checkHighlighting()
  }

  @Test
  fun testChooseFileIntentionForSystemDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency><caret>
                           <groupId>xxx</groupId>
                           <artifactId>xxx</artifactId>
                           <version>xxx</version>
                           <scope>system</system>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val action = getIntentionAtCaret("Choose File")
    assertNotNull(action)

    val libPath = myIndicesFixture!!.repositoryHelper.getTestDataPath("local1/junit/junit/4.0/junit-4.0.jar")
    val libFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(libPath)

    val intentionAction = IntentionActionDelegate.unwrap(action)
    (intentionAction as ChooseFileIntentionAction).setFileChooser { arrayOf(libFile) }
    val xmlSettings =
      CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings().getCustomSettings(XmlCodeStyleSettings::class.java)

    val prevValue = xmlSettings.XML_TEXT_WRAP
    try {
      // prevent file path from wrapping.
      xmlSettings.XML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
      myFixture.launchAction(action)
    }
    finally {
      xmlSettings.XML_TEXT_WRAP = prevValue
      intentionAction.setFileChooser(null)
    }

    val model = MavenDomUtil.getMavenDomProjectModel(myProject, myProjectPom)
    val dep = model!!.getDependencies().getDependencies()[0]

    assertEquals(findPsiFile(libFile), dep.getSystemPath().getValue())
  }

  @Test
  fun testNoChooseFileIntentionForNonSystemDependency() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency><caret>
                           <groupId>xxx</groupId>
                           <artifactId>xxx</artifactId>
                           <version>xxx</version>
                           <scope>compile</system>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    val action = getIntentionAtCaret("Choose File")
    assertNull(action)
  }

  @Test
  fun testTypeCompletion() = runBlocking {
    configureProjectPom("""
                          <groupId>test</groupId>
                          <artifactId>project</artifactId>
                          <version>1</version>
                          <dependencies>
                            <dependency>
                              <type><caret></type>
                            </dependency>
                          </dependencies>
                          """.trimIndent())

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "jar", "test-jar", "pom", "ear", "ejb", "ejb-client", "war", "bundle",
                             "jboss-har", "jboss-sar", "maven-plugin")
  }

  @Test
  fun testDoNotHighlightUnknownType() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <type>xxx</type>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testScopeCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <scope><caret></scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "compile", "provided", "runtime", "test", "system")
  }

  @Test
  fun testDoNotHighlightUnknownScopes() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <scope>xxx</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testPropertiesInScopes() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <my.scope>compile</my.scope>
                       </properties>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                           <scope>${'$'}{my.scope}</scope>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testDoesNotHighlightCorrectValues() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>4.0</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingVersionIfVersionIsWrong() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version><error>4.0.wrong</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }


  @Test
  fun testHighlightingArtifactIdAndVersionIfGroupIsUnknown() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><error>unknown</error></groupId>
                           <artifactId><error>junit</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingArtifactAndVersionIfGroupIsEmpty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId><error></error></groupId>
                           <artifactId><error>junit</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingVersionAndArtifactIfArtifactTheyAreFromAnotherGroup() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId><error>junit</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingVersionIfArtifactIsEmpty() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><error></error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingVersionIfArtifactIsUnknown() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId><error>unknown</error></artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingVersionItIsFromAnotherGroup() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>jmock</groupId>
                           <artifactId>jmock</artifactId>
                           <version><error>4.0</error></version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHighlightingCoordinatesWithClosedTags() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId/><error></error>
                           <artifactId/><error></error>
                           <version/><error></error>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHandlingProperties() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <dep.groupId>junit</dep.groupId>
                         <dep.artifactId>junit</dep.artifactId>
                         <dep.version>4.0</dep.version>
                       </properties>
                       """.trimIndent())
    importProjectAsync()

    // properties are taken from loaded project
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>${'$'}{dep.groupId}</groupId>
                           <artifactId>${'$'}{dep.artifactId}</artifactId>
                           <version>${'$'}{dep.version}</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testHandlingPropertiesWhenProjectIsNotYetLoaded() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <dep.groupId>junit</dep.groupId>
                         <dep.artifactId>junit</dep.artifactId>
                         <dep.version>4.0</dep.version>
                       </properties>
                       <dependencies>
                         <dependency>
                           <groupId>${'$'}{dep.groupId}</groupId>
                           <artifactId>${'$'}{dep.artifactId}</artifactId>
                           <version>${'$'}{dep.version}</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testDontHighlightProblemsInNonManagedPom1() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitVersion>4.0</junitVersion>
                       </properties>
                       """.trimIndent())

    val m = createModulePom("m1",
                            """
                                      <artifactId>m1</artifactId>
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                        <version>1</version>
                                      </parent>
                                      <dependencies>
                                       <dependency>
                                       <groupId>junit</groupId>
                                       <artifactId>junit</artifactId>
                                       <version>${'$'}{junitVersion}</version>
                                       </dependency>
                                      </dependencies>
                                      """.trimIndent())

    importProjectAsync()

    checkHighlighting(m)
  }

  @Test
  fun testDontHighlightProblemsInNonManagedPom2() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitVersion>4.0</junitVersion>
                       </properties>
                       """.trimIndent())

    val m = createModulePom("m1",
                            """
                                      <artifactId>m1</artifactId>
                                      <parent>
                                        <groupId>test</groupId>
                                        <artifactId>project</artifactId>
                                        <version>1</version>
                                      </parent>
                                      <properties>
                                       <aaa>${'$'}{junitVersion}</aaa>
                                      </properties>
                                      """.trimIndent())

    importProjectAsync()
    checkHighlighting(m)
  }

  @Test
  fun testExclusionCompletion() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <exclusion>
                               <groupId>jmock</groupId>
                               <artifactId><caret></artifactId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    assertCompletionVariants(myProjectPom, RENDERING_TEXT, "jmock")
  }


  @Test
  fun testDoNotHighlightUnknownExclusions() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <exclusion>
                               <groupId>foo</groupId>
                               <artifactId>bar</artifactId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testExclusionHighlightingAbsentGroupId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <<error descr="'groupId' child tag should be defined">exclusion</error>>
                               <artifactId>jmock</artifactId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testExclusionHighlightingAbsentArtifactId() = runBlocking {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <exclusions>
                             <<error descr="'artifactId' child tag should be defined">exclusion</error>>
                               <groupId>jmock</groupId>
                             </exclusion>
                           </exclusions>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testImportDependencyChainedProperty() = runBlocking {
    Assume.assumeTrue(isWorkspaceImport)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>pom</packaging>
                       <modules>
                          <module>m1</module>
                       </modules>
                       <dependencyManagement>
                           <dependencies>
                               <dependency>
                                   <groupId>org.deptest</groupId>
                                   <artifactId>bom-depparent</artifactId>
                                   <version>1.0</version>
                                   <type>pom</type>
                                   <scope>import</scope>
                               </dependency>
                           </dependencies>
                       </dependencyManagement>
                       """.trimIndent())

    createModulePom("m1", """
      <parent>
          <groupId>test</groupId>
          <artifactId>project</artifactId>
          <version>1</version>
        </parent>
      <artifactId>m1</artifactId>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>something</artifactId>
        </dependency>
      </dependencies>
      """.trimIndent())
    importProjectWithErrors()

    val model = MavenDomUtil.getMavenDomModel(myProject, myProjectPom, MavenDomProjectModel::class.java)

    val dependency = MavenDependencyCompletionUtil.findManagedDependency(model, myProject, "org.example", "something")
    assertNotNull(dependency)
    assertEquals("42", dependency.getVersion().getStringValue())
  }
}
