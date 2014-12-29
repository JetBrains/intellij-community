package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

public class DeleteAction extends BaseDeleteAction {
  public DeleteAction(String path, long checksum) {
    super(path, checksum);
  }

  public DeleteAction(DataInputStream in) throws IOException {
    super(in);
  }

  @Override
  protected boolean isModified(File toFile) throws IOException {
    return myChecksum != Digester.digestRegularFile(toFile);
  }
}
