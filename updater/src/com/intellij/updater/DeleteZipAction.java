package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

public class DeleteZipAction extends BaseDeleteAction {
  public DeleteZipAction(String path, long checksum) {
    super(path, checksum);
  }

  public DeleteZipAction(DataInputStream in) throws IOException {
    super(in);
  }

  @Override
  protected boolean isModified(File toFile) throws IOException {
    return myChecksum != Digester.digestFile(toFile);
  }
}
