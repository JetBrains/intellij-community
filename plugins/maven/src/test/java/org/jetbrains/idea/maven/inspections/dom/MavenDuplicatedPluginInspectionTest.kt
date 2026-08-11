package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicatePluginInspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDuplicatedPluginInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testDuplicatedPlugin() = runBlocking {
    maven.fixture.enableInspections(MavenDuplicatePluginInspection::class.java)

    maven.createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <build>
                         <plugins>
                           <<warning>plugin</warning>>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-jar-plugin</artifactId>
                             <version>2.2</version>
                           </plugin>
                           <<warning>plugin</warning>>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-jar-plugin</artifactId>
                             <version>2.2</version>
                           </plugin>
                         </plugins>
                       </build>
                       """.trimIndent())

    maven.checkHighlighting()
  }
}
