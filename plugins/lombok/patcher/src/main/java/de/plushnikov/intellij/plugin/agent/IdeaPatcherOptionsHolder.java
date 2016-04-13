package de.plushnikov.intellij.plugin.agent;

import org.apache.commons.lang3.StringUtils;

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

    for (String argString : StringUtils.split(args, ",")) {
      String[] argKeyValue = StringUtils.split(argString, "=");
      if (argKeyValue.length == 2) {
        options.put(argKeyValue[0], argKeyValue[1]);
      }
    }
  }

  public String getOption(String name) {
    return this.options.get(name);
  }
}
