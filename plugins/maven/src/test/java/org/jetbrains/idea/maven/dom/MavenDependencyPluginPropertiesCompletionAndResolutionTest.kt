// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiReference
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.junit.Test

class MavenDependencyPluginPropertiesCompletionAndResolutionTest : MavenDomTestCase() {

  companion object {
    private const val DEP_BLOCK = """
      <dependencies>
                      <dependency>
                          <groupId>mygroup</groupId>
                          <artifactId>myartifact</artifactId>
                          <version>1.0</version>
                      </dependency>
                      <dependency>
                          <groupId>mygroup</groupId>
                          <artifactId>myartifact</artifactId>
                          <version>1.0</version>
                          <type>type</type>
                      </dependency>
                      <dependency>
                          <groupId>mygroup</groupId>
                          <artifactId>myartifact</artifactId>
                          <version>1.0</version>
                          <type>type</type>
                          <classifier>classifier</classifier>
                      </dependency>
                      <dependency>
                          <groupId>mygroup</groupId>
                          <artifactId>myartifact</artifactId>
                          <version>1.0</version>
                          <classifier>classifier</classifier>
                      </dependency>
                      <dependency>
                          <groupId>anothergroup</groupId>
                          <artifactId>anotherartifact</artifactId>
                          <version>1.0</version>
                      </dependency>
                    </dependencies>
    """

    private const val PLUGIN_BLOCK = """
      <build>
                      <plugins>
                        <plugin>
                           <groupId>org.apache.maven.plugins</groupId>
                           <artifactId>maven-dependency-plugin</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>properties</goal>
                               </goals>
                             </execution>
                           </executions>
                          </plugin>
                       </plugins>
                    </build>
    """
  }

  override fun setUp() = runBlocking {
    super.setUp()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
                    
                        """.trimIndent())
    awaitConfiguration()
  }

  @Test
  fun testPropertiesResolvedToCorrespondingSimpleDependency() = runBlocking {
    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, null)
    }

  }

  @Test
  fun testPropertiesResolvedToCorrespondingDependencyWithType() = runBlocking {
    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifactWithType>${'$'}{<caret>mygroup:myartifact:type}</pathToArtifactWithType>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", "type", null)
    }

  }

  @Test
  fun testPropertiesResolvedToCorrespondingDependencyWithTypeAndClassifier() = runBlocking {
    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifactWithTypeAndClassifier>${'$'}{<caret>mygroup:myartifact:type:classifier}</pathToArtifactWithTypeAndClassifier>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", "type", "classifier")
    }

  }

  @Test
  fun testPropertiesResolvedToCorrespondingDependencyWithClassifier() = runBlocking {
    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifactWithClassifier>${'$'}{<caret>mygroup:myartifact:jar:classifier}</pathToArtifactWithTypeAndClassifier>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, "classifier")
    }

  }

  @Test
  fun testPropertiesResolvedWhenDependencyDefinedInProfiles() = runBlocking {
    val settingsFile = updateSettingsXml("""
      <profiles>
        <profile>
          <id>myprofile</id>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
          $DEP_BLOCK
        </profile>
      </profiles>
    """.trimIndent())

    importProjectAsync()
    awaitConfiguration()

    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>

                    $PLUGIN_BLOCK
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertNotNull("there is no reference to ${"mygroup"}:${"myartifact"}:${null}:${null} under caret ", ref)
      val resolved = ref!!.resolve()
      assertNotNull("reference to ${"mygroup"}:${"myartifact"}:${null}:${null} is not resolved ", resolved)
      assertEquals("PSI reference is not resolved to settings", resolved!!.containingFile.virtualFile, settingsFile)
    }
  }

  @Test
  fun testPropertiesResolvedWhenPluginDefinedInProfiles() = runBlocking {
    updateSettingsXml("""
      <profiles>
        <profile>
          <id>myprofile</id>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
          $PLUGIN_BLOCK
        </profile>
      </profiles>
    """.trimIndent())

    importProjectAsync()
    awaitConfiguration()

    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>

                    $DEP_BLOCK
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, null)
    }
  }

  @Test
  fun testPropertiesResolvedWhenPluginDefinedUsingProperties() = runBlocking {
    updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>

                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>
                    
                    $DEP_BLOCK
                    
                     <build>
                      <plugins>
                        <plugin>
                           <groupId>${'$'}{myPluginGroupId}</groupId>
                           <artifactId>${'$'}{myPluginArtifactId}</artifactId>
                           <executions>
                             <execution>
                               <goals>
                                 <goal>properties</goal>
                               </goals>
                             </execution>
                           </executions>
                          </plugin>
                       </plugins>
                    </build>
""")
    val ref = getReferenceAtCaret(projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, null)
    }
  }

  private fun assertRefFor(ref: PsiReference?, group: String, artifact: String, type: String?, classifier: String?) {
    assertNotNull("there is no reference to $group:$artifact:$type:$classifier under caret ", ref)
    val resolved = ref!!.resolve()
    assertNotNull("reference to $group:$artifact:$type:$classifier is not resolved ", resolved)
    val mavenDomModel = MavenDomUtil.getMavenDomModel(project, projectPom, MavenDomProjectModel::class.java)
    assertNotNull("cannot get maven dom model", mavenDomModel)
    val dependencies = mavenDomModel!!.dependencies.dependencies.associate {
      """${it.groupId}:${it.artifactId}:${it.type.stringValue}:${it.classifier.stringValue}""" to it.xmlTag
    }
    val key = "$group:$artifact:$type:$classifier"
    assertSame("PSI reference is not resolved to corresponding dependency", dependencies[key], resolved)
  }
}