/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.execution;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.util.Arrays;

public class MavenRunConfigurationTest extends IdeaTestCase {
  @Override
  protected void tearDown() throws Exception {
    MavenServerManager.getInstance().shutdown(true);
    super.tearDown();
  }

  public void testSaveLoadRunnerParameters() {
    MavenRunConfiguration.MavenSettings s = new MavenRunConfiguration.MavenSettings(myProject);
    s.myRunnerParameters.setWorkingDirPath("some path");
    s.myRunnerParameters.setGoals(Arrays.asList("clean", "validate"));
    s.myRunnerParameters.setProfilesMap(ImmutableMap.<String, Boolean>builder()
                                          .put("prof1", true)
                                          .put("prof2", true)
                                          .put("prof3", false)
                                          .put("aaa", true)
                                          .put("tomcat (local)", false)
                                          .put("tomcat (local) ", true).build());

    s.myGeneralSettings = new MavenGeneralSettings();
    s.myGeneralSettings.setChecksumPolicy(MavenExecutionOptions.ChecksumPolicy.WARN);
    s.myGeneralSettings.setFailureBehavior(MavenExecutionOptions.FailureMode.AT_END);
    s.myGeneralSettings.setOutputLevel(MavenExecutionOptions.LoggingLevel.FATAL);
    s.myGeneralSettings.setThreads("1.5C");

    s.myRunnerSettings = new MavenRunnerSettings();
    s.myRunnerSettings.setMavenProperties(ImmutableMap.of("a", "1", "b", "2", "c", "3"));

    Element xml = XmlSerializer.serialize(s);
    MavenRunConfiguration.MavenSettings loaded
      = XmlSerializer.deserialize(xml, MavenRunConfiguration.MavenSettings.class);
    
    assertEquals(s.myRunnerParameters.getWorkingDirPath(), loaded.myRunnerParameters.getWorkingDirPath());
    assertEquals(s.myRunnerParameters.getGoals(), loaded.myRunnerParameters.getGoals());
    assertEquals(s.myRunnerParameters.getProfilesMap(), loaded.myRunnerParameters.getProfilesMap());
    assertOrderedEquals(s.myRunnerParameters.getProfilesMap().keySet(), loaded.myRunnerParameters.getProfilesMap().keySet()); // Compare ordering of profiles.

    assertEquals(s.myGeneralSettings, loaded.myGeneralSettings);
    assertEquals(s.myRunnerSettings, loaded.myRunnerSettings);
  }

  public void testMavenParametersEditing() throws ConfigurationException {
    ImmutableMap<String, Boolean> profilesMap = ImmutableMap.<String, Boolean>builder()
      .put("prof1", true)
      .put("prof2", true)
      .put("prof3", false)
      .put("aaa", true)
      .put("tomcat (local)", false)
      .put("tomcat (local) ", true).build();

    final MavenRunnerParameters params = new MavenRunnerParameters();
    params.setProfilesMap(profilesMap);

    MavenRunnerParametersConfigurable cfg = new MavenRunnerParametersConfigurable(getProject()) {
      @Override
      protected MavenRunnerParameters getParameters() {
        return params;
      }
    };

    cfg.reset();

    cfg.apply();

    assertEquals(profilesMap, cfg.getParameters().getProfilesMap());
  }
}
