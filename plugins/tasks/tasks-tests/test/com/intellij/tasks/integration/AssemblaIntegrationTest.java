/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.tasks.integration;

import com.intellij.tasks.Task;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.tasks.TaskTestUtil.TaskBuilder;
import static com.intellij.tasks.TaskTestUtil.assertTasksEqual;

/**
 * @author Dmitry Avdeev
 */
public class AssemblaIntegrationTest extends GenericSubtypeTestCase {

  public static final String TASK_LIST_RESPONSE =
    """
      <?xml version="1.0" encoding="UTF-8"?>
      <tickets type="array">
      <ticket>
        <assigned-to-id>dsNkyYr0Gr4iEBeJe5cbCb</assigned-to-id>
        <completed-date type="datetime"></completed-date>
        <component-id type="integer"></component-id>
        <created-on type="datetime">2013-04-01T10:45:06+03:00</created-on>
        <description></description>
        <from-support type="integer">0</from-support>
        <id type="integer">50351983</id>
        <importance type="integer">-1</importance>
        <importance-float type="float">-1.0</importance-float>
        <is-story type="boolean">false</is-story>
        <milestone-id type="integer"></milestone-id>
        <notification-list>dsNkyYr0Gr4iEBeJe5cbCb</notification-list>
        <number type="integer">1</number>
        <priority type="integer">3</priority>
        <reporter-id>dsNkyYr0Gr4iEBeJe5cbCb</reporter-id>
        <space-id>ab1WOCMQar4QGgacwqjQWU</space-id>
        <status type='integer'>0</status>
        <status-name>New</status-name>
        <story-importance type="integer">0</story-importance>
        <summary>&#1055;&#1088;&#1080;&#1074;&#1077;&#1090;</summary>
        <updated-at type="datetime">2013-04-01T10:48:19+03:00</updated-at>
        <working-hours type="float">0.0</working-hours>
        <working-hour type="float" warning="deprecated">0.0</working-hour>
        <estimate type="string">Small</estimate>
        <total-estimate type="float">1.0</total-estimate>
        <invested-hours type="float">0.0</invested-hours>
        <assigned-to><id>dsNkyYr0Gr4iEBeJe5cbCb</id><login>avdeev.dmitry</login><login_name warning="deprecated">avdeev.dmitry</login_name><name>avdeev.dmitry</name></assigned-to>
        <reporter><id>dsNkyYr0Gr4iEBeJe5cbCb</id><login>avdeev.dmitry</login><login_name warning="deprecated">avdeev.dmitry</login_name><name>avdeev.dmitry</name></reporter>
      </ticket>
      </tickets>""";

  @NotNull
  @Override
  protected GenericRepository createRepository(GenericRepositoryType genericType) {
    return (GenericRepository)genericType.new AssemblaRepository().createRepository();
  }

  public void testParsingTaskList() throws Exception {
    Task[] tasks = myRepository.getActiveResponseHandler().parseIssues(TASK_LIST_RESPONSE, 50);
    assertTasksEqual(
      new Task[]{
        new TaskBuilder("1", "\u041F\u0440\u0438\u0432\u0435\u0442", myRepository)
          .withDescription("")
          .withUpdated("2013-04-01T10:48:19+03:00")
          .withCreated("2013-04-01T10:45:06+03:00")
      },
      tasks);
  }
}
