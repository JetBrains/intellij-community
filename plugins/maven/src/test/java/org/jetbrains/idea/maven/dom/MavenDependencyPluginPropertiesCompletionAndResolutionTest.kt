// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.maven.testFramework.fixtures.updateSettingsXml
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiReference
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.fixtures.getReferenceAtCaret
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDependencyPluginPropertiesCompletionAndResolutionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(initialPom = INITIAL_POM, mavenVersion = mavenVersion, modelVersion = modelVersion)

  @Test
  fun testPropertiesResolvedToCorrespondingSimpleDependency() = runBlocking {
    maven.updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, null)
    }
  }

  @Test
  fun testPropertiesResolvedToCorrespondingDependencyWithType() = runBlocking {
    maven.updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifactWithType>${'$'}{<caret>mygroup:myartifact:type}</pathToArtifactWithType>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", "type", null)
    }
  }

  @Test
  fun testPropertiesResolvedToCorrespondingDependencyWithTypeAndClassifier() = runBlocking {
    maven.updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifactWithTypeAndClassifier>${'$'}{<caret>mygroup:myartifact:type:classifier}</pathToArtifactWithTypeAndClassifier>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", "type", "classifier")
    }
  }

  @Test
  fun testPropertiesResolvedToCorrespondingDependencyWithClassifier() = runBlocking {
    maven.updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifactWithClassifier>${'$'}{<caret>mygroup:myartifact:jar:classifier}</pathToArtifactWithTypeAndClassifier>
                    </properties>
                    
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
""")
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, "classifier")
    }
  }

  @Test
  fun testPropertiesResolvedWhenDependencyDefinedInProfiles() = runBlocking {
    val settingsFile = maven.updateSettingsXml("""
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

    maven.importProjectAsync()
    maven.awaitConfiguration()

    maven.updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>

                    $PLUGIN_BLOCK
""")
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertNotNull(ref, "there is no reference to mygroup:myartifact:null:null under caret ")
      val resolved = ref!!.resolve()
      assertNotNull(resolved, "reference to mygroup:myartifact:null:null is not resolved ")
      assertEquals(resolved!!.containingFile.virtualFile, settingsFile, "PSI reference is not resolved to settings")
    }
  }

  @Test
  fun testPropertiesResolvedWhenPluginDefinedInProfiles() = runBlocking {
    maven.updateSettingsXml("""
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

    maven.importProjectAsync()
    maven.awaitConfiguration()

    maven.updateProjectPom("""
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                        <pathToArtifact>${'$'}{<caret>mygroup:myartifact:jar}</pathToMyGroup>
                    </properties>

                    $DEP_BLOCK
""")
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, null)
    }
  }

  @Test
  fun testPropertiesResolvedWhenPluginDefinedUsingProperties() = runBlocking {
    maven.updateProjectPom("""
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
    val ref = maven.getReferenceAtCaret(maven.projectPom)
    readAction {
      assertRefFor(ref, "mygroup", "myartifact", null, null)
    }
  }

  private fun assertRefFor(ref: PsiReference?, group: String, artifact: String, type: String?, classifier: String?) {
    assertNotNull(ref, "there is no reference to $group:$artifact:$type:$classifier under caret ")
    val resolved = ref!!.resolve()
    assertNotNull(resolved, "reference to $group:$artifact:$type:$classifier is not resolved ")
    val mavenDomModel = MavenDomUtil.getMavenDomModel(maven.project, maven.projectPom, MavenDomProjectModel::class.java)
    assertNotNull(mavenDomModel, "cannot get maven dom model")
    val dependencies = mavenDomModel!!.dependencies.dependencies.associate {
      """${it.groupId}:${it.artifactId}:${it.type.stringValue}:${it.classifier.stringValue}""" to it.xmlTag
    }
    val key = "$group:$artifact:$type:$classifier"
    assertSame(dependencies[key], resolved, "PSI reference is not resolved to corresponding dependency")
  }

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

    private val INITIAL_POM = """
      <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    $DEP_BLOCK
                    $PLUGIN_BLOCK
    """.trimIndent()
  }
}
