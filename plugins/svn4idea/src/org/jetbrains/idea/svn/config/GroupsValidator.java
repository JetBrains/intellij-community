// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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
