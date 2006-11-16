package com.intellij.ide.fileTemplates.impl;

import javax.swing.*;

/**
 * author: lesya
 */
public class FileTemplateDescriptionImpl {
  private final String myTitle;
  private final Icon myIcon;

  public FileTemplateDescriptionImpl(String title, Icon icon) {
    myTitle = title;
    myIcon = icon;
  }

  protected FileTemplateTabAsTree.FileTemplateNode createTreeNode(){
    return new FileTemplateTabAsTree.FileTemplateNode(myIcon, myTitle);
  }
}
