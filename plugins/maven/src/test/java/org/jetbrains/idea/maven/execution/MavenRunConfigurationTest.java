/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

import java.util.Arrays;

public class MavenRunConfigurationTest extends IdeaTestCase {
  public void testSaveLoadRunnerParameters() {
    MavenRunConfiguration.MavenSettings s = new MavenRunConfiguration.MavenSettings(myProject);
    s.myRunnerParameters.setWorkingDirPath("some path");
    s.myRunnerParameters.setGoals(Arrays.asList("clean", "validate"));
    s.myRunnerParameters.setProfilesMap(ImmutableMap.of("prof1", true, "prof2", true, "prof3", false, "aaa", true));

    s.myGeneralSettings = new MavenGeneralSettings();
    s.myGeneralSettings.setChecksumPolicy(MavenExecutionOptions.ChecksumPolicy.WARN);
    s.myGeneralSettings.setFailureBehavior(MavenExecutionOptions.FailureMode.AT_END);
    s.myGeneralSettings.setOutputLevel(MavenExecutionOptions.LoggingLevel.FATAL);

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
}
