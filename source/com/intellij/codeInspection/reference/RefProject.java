/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 16, 2001
 * Time: 12:50:45 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

public class RefProject extends RefEntity {
  private final RefManager myRefManager;
  private RefPackage myDefaultPackage;

  public RefProject(RefManager refManager) {
    super(RefUtil.getProjectFileName(refManager.getProject()));
    myRefManager = refManager;
  }

  private RefManager getRefManager() {
    return myRefManager;
  }

  public RefPackage getDefaultPackage() {
    if (myDefaultPackage == null) {
      myDefaultPackage = getRefManager().getPackage("default package");
    }

    return myDefaultPackage;
  }
}
