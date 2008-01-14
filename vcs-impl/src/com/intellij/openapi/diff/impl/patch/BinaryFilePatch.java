package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

/**
 * @author yole
 */
public class BinaryFilePatch extends FilePatch {
  private byte[] myBeforeContent;
  private byte[] myAfterContent;

  public BinaryFilePatch(final byte[] beforeContent, final byte[] afterContent) {
    myBeforeContent = beforeContent;
    myAfterContent = afterContent;
  }

  protected void applyCreate(final VirtualFile newFile) throws IOException, ApplyPatchException {
    newFile.setBinaryContent(myAfterContent);
  }

  protected ApplyPatchStatus applyChange(final VirtualFile fileToPatch) throws IOException, ApplyPatchException {
    fileToPatch.setBinaryContent(myAfterContent);
    return ApplyPatchStatus.SUCCESS;
  }

  public boolean isNewFile() {
    return myBeforeContent == null;
  }

  public boolean isDeletedFile() {
    return myAfterContent == null;
  }
}
