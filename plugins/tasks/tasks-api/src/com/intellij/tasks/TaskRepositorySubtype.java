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
package com.intellij.tasks;

import javax.swing.*;

/**
 * This auxiliary interface was added to support creation of configured generic repositories.
 * Every TaskRepositoryType subclass can be considered subtype of its own.
 *
 * @author Mikhail Golubev
 */
public interface TaskRepositorySubtype {
  String getName();

  Icon getIcon();

  TaskRepository createRepository();
}
