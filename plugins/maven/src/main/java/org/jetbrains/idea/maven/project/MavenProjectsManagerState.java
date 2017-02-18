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

/*
 * Created by IntelliJ IDEA.
 * User: Vladislav.Kaznacheev
 * Date: Jun 29, 2007
 * Time: 1:02:29 PM
 */
package org.jetbrains.idea.maven.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MavenProjectsManagerState {
  public List<String> originalFiles = new ArrayList<>();

  public Set<String> ignoredFiles = new TreeSet<>();
  public List<String> ignoredPathMasks = new ArrayList<>();
}