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
package com.intellij.tasks.jira;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Mikhail Golubev
 */
public class JiraUtil {
  public static final Gson GSON = buildGson();

  private static Gson buildGson() {
    GsonBuilder gson = new GsonBuilder();
    // ISO-8601 with timezone info
    gson.setDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSZ");
    return gson.create();
  }
}
