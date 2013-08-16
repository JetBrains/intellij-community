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
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.tasks.generic.GenericRepository;
import com.intellij.tasks.generic.GenericRepositoryType;
import com.intellij.tasks.generic.GenericTask;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class AsanaGenericIntegrationTest extends TaskManagerTestCase {
  private static final String RESPONSE = "" +
   "{\n" +
   "  \"data\": [\n" +
   "    {\n" +
   "      \"id\": 5479650606120,\n" +
   "      \"name\": \"Task #1\"\n" +
   "    },\n" +
   "    {\n" +
   "      \"id\": 5202014833559,\n" +
   "      \"name\": \"Task #2\"\n" +
   "    }\n" +
   "  ]\n" +
   "}";

  private GenericRepository myRepository;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    GenericRepositoryType genericType = new GenericRepositoryType();
    myRepository = (GenericRepository) genericType.new AsanaRepository().createRepository();
  }

  public void testParsing() throws Exception {
    Task[] tasks = myRepository.getActiveResponseHandler().parseIssues(RESPONSE);
    List<Task> expected = ContainerUtil.<Task>newArrayList(
      new GenericTask("5479650606120", "Task #1", myRepository),
      new GenericTask("5202014833559", "Task #2", myRepository)
    );
    assertEquals(expected, Arrays.asList(tasks));
  }
}
