/**
 * @author cdr
 */
package com.intellij.j2ee.make;

import com.intellij.openapi.module.Module;

public abstract class BuildInstructionBase implements BuildInstruction, Cloneable {
  private final String myOutputRelativePath;
  private final Module myModule;

  protected BuildInstructionBase(String outputRelativePath, Module module) {
    myOutputRelativePath = outputRelativePath;
    myModule = module;
  }

  public String getOutputRelativePath() {
    return myOutputRelativePath;
  }

  public Module getModule() {
    return myModule;
  }

  public BuildInstructionBase clone() {
    try {
      return (BuildInstructionBase)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean isExternalDependencyInstruction() {
    return getOutputRelativePath().startsWith("..");
  }
}