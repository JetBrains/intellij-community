package com.pme.exe.res.vi;

/**
 * @author yole
 */
public class Var extends VersionInfoBin {
  public Var(String name) {
    super(name, "Translation");
    addMember(new DWord("Translation"));
  }
}
