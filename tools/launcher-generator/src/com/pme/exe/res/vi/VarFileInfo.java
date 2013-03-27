package com.pme.exe.res.vi;

/**
 * @author yole
 */
public class VarFileInfo extends VersionInfoBin {
  public VarFileInfo() {
    super("VarFileInfo", "VarFileInfo", new VersionInfoFactory() {
      @Override
      public VersionInfoBin createChild(int index) {
        return new Var("Var" + index);
      }
    });
  }
}
