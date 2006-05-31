package com.intellij.help.impl;

import javax.help.DefaultHelpModel;
import javax.help.HelpSet;
import javax.help.JHelp;
import java.util.Vector;

/**
 * It a dirty patch! Help system is so ugly that it hangs when it open some "external" links.
 * To prevent this we open "external" links in nornal WEB browser.
 *
 * @author Vladimir Kondratyev
 */
class IdeaJHelp extends JHelp{
  /**
   * PATCHED VERSION OF SUPER CONSTRUCTOR
   */
  public IdeaJHelp(HelpSet hs){
    super(new DefaultHelpModel(hs));

    navigators=new Vector();
    navDisplayed=true;

    // HERE -- need to do something about doc title changes....

    this.contentViewer=new IdeaJHelpContentViewer(helpModel);

    setModel(helpModel);
    if(helpModel!=null){
      setupNavigators();
    }

    updateUI();
  }
}