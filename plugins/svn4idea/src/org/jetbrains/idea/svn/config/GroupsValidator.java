package org.jetbrains.idea.svn.config;

import java.util.ArrayList;
import java.util.List;

public class GroupsValidator implements Runnable {
  private final List<SvnConfigureProxiesComponent> myComponents;
  private final ValidationListener myListener;
  private boolean myStopped;

  public GroupsValidator(final ValidationListener listener) {
    myComponents = new ArrayList<SvnConfigureProxiesComponent>();
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
