package com.intellij.help.impl;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginDescriptor;

import javax.help.BadIDException;
import javax.help.HelpSet;
import javax.help.HelpSetException;
import java.awt.*;
import java.net.URL;

public class HelpManagerImpl extends HelpManager implements ApplicationComponent {
  private HelpSet myHelpSet = null;
  private IdeaHelpBroker myBroker = null;

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void invokeHelp(String id) {
    if (myHelpSet == null) {
      try {
        myHelpSet = createHelpSet();
      } catch (Exception ex) {
      }
    }
    if (myHelpSet == null) {
      Messages.showMessageDialog("Help not found for " + id, "Help Not Found", Messages.getErrorIcon());
      return;
    }
    if (myBroker == null) {
      myBroker = new IdeaHelpBroker(myHelpSet);
    }

    Window activeWindow=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    myBroker.setActivationWindow(activeWindow);
    /*
    URL currentURL = myBroker.getCurrentURL();
    System.out.println("currentURL = " + currentURL);
    */
    if (id != null) {
      try {
        myBroker.setCurrentID(id);
      }
      catch (BadIDException e) {
        Messages.showMessageDialog("Help topic \"" + id + "\" not found", "Error", Messages.getErrorIcon());
        return;
      }
    }
    myBroker.setDisplayed(true);
  }

  public String getComponentName() {
    return "HelpManager";
  }

  private static HelpSet createHelpSet() {
    // This is a temporary solution: path to the help should be customized somehow
    String urlToHelp = PathManager.getHelpURL() + "/" + "Help.hs";

    try {
      HelpSet helpSet = new HelpSet(null, new URL (urlToHelp));

      // merge plugins help sets
      PluginDescriptor [] pluginDescriptors = PluginManager.getPlugins();
      for (int i = 0; i < pluginDescriptors.length; i++) {
        PluginDescriptor pluginDescriptor = pluginDescriptors[i];
        if (pluginDescriptor.getHelpSets() != null && pluginDescriptor.getHelpSets().length > 0) {
          for (int j = 0; j < pluginDescriptor.getHelpSets().length; j++) {
            PluginDescriptor.HSPath hsPath = pluginDescriptor.getHelpSets()[j];

            URL hsURL = new URL("jar:file:///" + pluginDescriptor.getPath().getAbsolutePath() + "/help/" + hsPath.getFile() + "!" + hsPath.getPath());
            try {
              HelpSet pluginHelpSet = new HelpSet (null, hsURL);
              helpSet.add(pluginHelpSet);
            }
            catch (HelpSetException e) {
              e.printStackTrace();
              // damn
            }
          }
        }
      }

      return helpSet;
    }
    catch (Exception ee) {
      System.err.println("HelpSet " + urlToHelp + " not found");
      return null;
    }
  }
}
