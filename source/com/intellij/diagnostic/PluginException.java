package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginDescriptor;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Jan 8, 2004
 * Time: 3:06:43 PM
 * To change this template use Options | File Templates.
 */
public class PluginException extends RuntimeException {
  private PluginDescriptor myDescriptor;

  public PluginException(Throwable e, PluginDescriptor descriptor) {
    super (e.getMessage(), e);
    myDescriptor = descriptor;
  }

  public PluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  public String getMessage() {
    String message = super.getMessage();

    if (message == null)
      message = "";

    message += " [Plugin: " + myDescriptor.getName() + "]";
    return message;
  }
}
