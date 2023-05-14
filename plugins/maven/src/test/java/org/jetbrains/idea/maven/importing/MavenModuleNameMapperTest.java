package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MavenModuleNameMapperTest {

  @Test
  public void shouldMapUniqueArtifactIds() {
    MavenProject project1 = mock(MavenProject.class);
    when(project1.getMavenId()).thenReturn(new MavenId("com.demo:maven-module-1"));
    when(project1.getPath()).thenReturn("maven-module-path-1");
    MavenProject project2 = mock(MavenProject.class);
    when(project2.getMavenId()).thenReturn(new MavenId("com.demo:maven-module-2"));
    when(project2.getPath()).thenReturn("maven-module-path-2");

    Collection<MavenProject> projects = List.of(
      project1,
      project2
    );
    Map<MavenProject, Module> mavenProjectToModule = new HashMap<>();
    Map<MavenProject, String> mavenProjectToModuleName = new HashMap<>();
    Map<MavenProject, String> mavenProjectToModulePath = new HashMap<>();
    String dedicatedModuleDir = null;

    MavenModuleNameMapper.map(
      projects,
      mavenProjectToModule,
      mavenProjectToModuleName,
      mavenProjectToModulePath,
      dedicatedModuleDir
    );

    assertEquals(Map.of(
      project1, "maven-module-1",
      project2, "maven-module-2"
    ), mavenProjectToModuleName);
  }

  @Test
  public void shouldMapNonUniqueArtifactIdsCaseSensitive() {
    MavenProject project1 = mock(MavenProject.class);
    when(project1.getMavenId()).thenReturn(new MavenId("com.demo:maven-module"));
    when(project1.getPath()).thenReturn("maven-module-path-1");
    MavenProject project2 = mock(MavenProject.class);
    when(project2.getMavenId()).thenReturn(new MavenId("com.demo.sub:maven-module"));
    when(project2.getPath()).thenReturn("maven-module-path-2");

    Collection<MavenProject> projects = List.of(
      project1,
      project2
    );
    Map<MavenProject, Module> mavenProjectToModule = new HashMap<>();
    Map<MavenProject, String> mavenProjectToModuleName = new HashMap<>();
    Map<MavenProject, String> mavenProjectToModulePath = new HashMap<>();
    String dedicatedModuleDir = null;

    MavenModuleNameMapper.map(
      projects,
      mavenProjectToModule,
      mavenProjectToModuleName,
      mavenProjectToModulePath,
      dedicatedModuleDir
    );

    assertEquals(Map.of(
      project1, "maven-module (1) (com.demo)",
      project2, "maven-module (2) (com.demo.sub)"
    ), mavenProjectToModuleName);
  }

  // covers IDEA-320329
  @Test
  public void shouldMapNonUniqueArtifactIdsNonCaseSensitive() {
    MavenProject project1 = mock(MavenProject.class);
    when(project1.getMavenId()).thenReturn(new MavenId("com.demo:MavenModule"));
    when(project1.getPath()).thenReturn("maven-module-path-1");
    MavenProject project2 = mock(MavenProject.class);
    when(project2.getMavenId()).thenReturn(new MavenId("com.demo.sub:mavenmodule"));
    when(project2.getPath()).thenReturn("maven-module-path-2");

    Collection<MavenProject> projects = List.of(
      project1,
      project2
    );
    Map<MavenProject, Module> mavenProjectToModule = new HashMap<>();
    Map<MavenProject, String> mavenProjectToModuleName = new HashMap<>();
    Map<MavenProject, String> mavenProjectToModulePath = new HashMap<>();
    String dedicatedModuleDir = null;

    MavenModuleNameMapper.map(
      projects,
      mavenProjectToModule,
      mavenProjectToModuleName,
      mavenProjectToModulePath,
      dedicatedModuleDir
    );

    assertEquals(Map.of(
      project1, "MavenModule (1) (com.demo)",
      project2, "mavenmodule (2) (com.demo.sub)"
    ), mavenProjectToModuleName);
  }}
