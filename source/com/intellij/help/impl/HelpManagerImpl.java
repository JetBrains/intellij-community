package com.intellij.help.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NonNls;

import javax.help.BadIDException;
import javax.help.HelpSet;
import javax.help.HelpSetException;
import java.awt.*;
import java.net.URL;

public class HelpManagerImpl extends HelpManager implements ApplicationComponent {
  private HelpSet myHelpSet = null;
  private IdeaHelpBroker myBroker = null;
  @NonNls private static final String HELP_HS = "Help.hs";

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
      Messages.showMessageDialog(IdeBundle.message("help.not.found.error", id),
                                 IdeBundle.message("help.not.found.title"), Messages.getErrorIcon());
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
        Messages.showMessageDialog(IdeBundle.message("help.topic.not.found.error", id),
                                   CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
    }
    myBroker.setDisplayed(true);
  }

  public String getComponentName() {
    return "HelpManager";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static HelpSet createHelpSet() {
    // This is a temporary solution: path to the help should be customized somehow
    String urlToHelp = PathManager.getHelpURL() + "/" + HELP_HS;

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
