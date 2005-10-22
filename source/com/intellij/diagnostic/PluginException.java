package com.intellij.diagnostic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Jan 8, 2004
 * Time: 3:06:43 PM
 * To change this template use Options | File Templates.
 */
public class PluginException extends RuntimeException {
  private IdeaPluginDescriptor myDescriptor;

  public PluginException(Throwable e, IdeaPluginDescriptor descriptor) {
    super (e.getMessage(), e);
    myDescriptor = descriptor;
  }

  public IdeaPluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  public String getMessage() {
    @NonNls String message = super.getMessage();

    if (message == null)
      message = "";

    message += " [Plugin: " + myDescriptor.getName() + "]";
    return message;
  }
}
