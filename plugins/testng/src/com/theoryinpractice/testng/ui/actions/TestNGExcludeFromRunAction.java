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

/*
 * User: anna
 * Date: 15-Jun-2010
 */
package com.theoryinpractice.testng.ui.actions;

import com.intellij.execution.actions.AbstractExcludeFromRunAction;
import com.intellij.execution.configurations.RunConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.model.TestType;

import java.util.Set;

public class TestNGExcludeFromRunAction extends AbstractExcludeFromRunAction<TestNGConfiguration> {

  @Override
  protected Set<String> getPattern(TestNGConfiguration configuration) {
    return configuration.getPersistantData().getPatterns();
  }

  @Override
  protected boolean isPatternBasedConfiguration(RunConfiguration configuration) {
    return configuration instanceof TestNGConfiguration &&
           TestType.PATTERN.getType().equals(((TestNGConfiguration)configuration).getPersistantData().TEST_OBJECT);
  }
}