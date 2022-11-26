// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.util.xml.impl.ConvertContextFactory;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.junit.Test;

public class MavenArtifactCoordinatesHelperTest extends MavenDomTestCase {

  @Test
  public void testGetPluginVersionFromParentPluginManagement() {
    var parentFile = createProjectPom("""
                <groupId>group</groupId>
                <artifactId>parent</artifactId>
                <version>1</version>
                <packaging>pom</packaging>
                <build>
                  <pluginManagement>
                    <plugins>
                      <plugin>
                        <groupId>plugin-group</groupId>
                        <artifactId>plugin-artifact-id</artifactId>
                        <version>1.0.0</version>
                      </plugin>
                    </plugins>
                  </pluginManagement>
                </build>
                """);
    var m1File = createModulePom("m1", """
                <artifactId>m1</artifactId>
                <version>1</version>
                <parent>
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                </parent>
                <build>
                  <plugins>
                    <plugin>
                      <groupId>plugin-group</groupId>
                      <artifactId>plugin-artifact-id</artifactId>
                    </plugin>
                  </plugins>
                </build>
                """);
    importProject();

    var pluginVersion = "1.0.0";

    var mavenModel = MavenDomUtil.getMavenDomProjectModel(myProject, m1File);
    var coords = mavenModel.getBuild().getPlugins().getPlugins().get(0);
    var converterContext = ConvertContextFactory.createConvertContext(mavenModel);

    var mavenId = MavenArtifactCoordinatesHelper.getMavenId(coords, converterContext);

    assertEquals(pluginVersion, mavenId.getVersion());
  }

}
