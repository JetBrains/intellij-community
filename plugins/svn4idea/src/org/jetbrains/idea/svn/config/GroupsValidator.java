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
package org.jetbrains.idea.svn.config;

import java.util.ArrayList;
import java.util.List;

public class GroupsValidator implements Runnable {
  private final List<SvnConfigureProxiesComponent> myComponents;
  private final ValidationListener myListener;
  private boolean myStopped;

  public GroupsValidator(final ValidationListener listener) {
    myComponents = new ArrayList<>();
    myListener = listener;
  }

  public void add(final SvnConfigureProxiesComponent component) {
    myComponents.add(component);
  }

  public void run() {
    if (myStopped) {
      return;
    }
    myListener.onSuccess(); // clear status of previous validation

    boolean valid = true;
    for (SvnConfigureProxiesComponent component : myComponents) {
      if(! component.validate(myListener)) {
        valid = false;
        break;
      }
    }

    for (SvnConfigureProxiesComponent component : myComponents) {
      component.setIsValid(valid);
    }
  }

  public void stop() {
    myStopped = true;
  }
}
