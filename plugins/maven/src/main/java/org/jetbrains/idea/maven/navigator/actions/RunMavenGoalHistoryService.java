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
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;

/**
 * @author Sergey Evdokimov
 */
@State(
    name = "mvcRunTargetHistory",
    storages = @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )
)
public class RunMavenGoalHistoryService implements PersistentStateComponent<String[]> {

  private static final int MAX_HISTORY_LENGTH = 20;

  private final LinkedList<String> myHistory = new LinkedList<String>();

  @Nullable
  @Override
  public String[] getState() {
    return new String[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void loadState(String[] state) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
