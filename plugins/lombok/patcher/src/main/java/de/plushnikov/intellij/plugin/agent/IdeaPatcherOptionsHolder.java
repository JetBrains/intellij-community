package de.plushnikov.intellij.plugin.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Alexej Kubarev
 */
public class IdeaPatcherOptionsHolder {

  private static IdeaPatcherOptionsHolder INSTANCE;
  private final Map<String, String> options = new HashMap<String, String>();

  private IdeaPatcherOptionsHolder() {
  }


  public static IdeaPatcherOptionsHolder getInstance() {
    if (null == INSTANCE) {
      INSTANCE = new IdeaPatcherOptionsHolder();
    }

    return INSTANCE;
  }

  public void addAll(String args) {
    if (null == args) {
      return;
    }

    for (String argString : args.split(",")) {
      String[] argKeyValue = argString.split("=");
      if (argKeyValue.length == 2) {
        options.put(argKeyValue[0], argKeyValue[1]);
      }
    }
  }

  public String getOption(String name) {
    return this.options.get(name);
  }
}
