package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.LineTokenizer;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;

/**
 * @author yole
 */
public class TextFilePatch extends FilePatch {
  private final List<PatchHunk> myHunks = new ArrayList<PatchHunk>();

  public void addHunk(final PatchHunk hunk) {
    myHunks.add(hunk);
  }

  public List<PatchHunk> getHunks() {
    return Collections.unmodifiableList(myHunks);
  }

  protected ApplyPatchStatus applyChange(final VirtualFile fileToPatch) throws IOException, ApplyPatchException {
    byte[] fileContents = fileToPatch.contentsToByteArray();
    CharSequence text = LoadTextUtil.getTextByBinaryPresentation(fileContents, fileToPatch);
    StringBuilder newText = new StringBuilder();
    ApplyPatchStatus status = applyModifications(text, newText);
    if (status != ApplyPatchStatus.ALREADY_APPLIED) {
      final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
      if (document == null) {
        throw new ApplyPatchException("Failed to set contents for updated file " + fileToPatch.getPath());
      }
      document.setText(newText.toString());
      FileDocumentManager.getInstance().saveDocument(document);
    }
    return status;
  }

  public ApplyPatchStatus applyModifications(final CharSequence text, final StringBuilder newText) throws ApplyPatchException {
    if (myHunks.size() == 0) {
      return ApplyPatchStatus.SUCCESS;
    }
    List<String> lines = new ArrayList<String>();
    Collections.addAll(lines, LineTokenizer.tokenize(text, false));
    ApplyPatchStatus result = null;
    for(PatchHunk hunk: myHunks) {
      result = ApplyPatchStatus.and(result, hunk.apply(lines));
    }
    for(int i=0; i<lines.size(); i++) {
      newText.append(lines.get(i));
      if (i < lines.size()-1 || !myHunks.get(myHunks.size()-1).isNoNewLineAtEnd()) {
        newText.append("\n");
      }
    }
    return result;
  }

  protected void applyCreate(final VirtualFile newFile) throws IOException, ApplyPatchException {
    final Document document = FileDocumentManager.getInstance().getDocument(newFile);
    if (document == null) {
      throw new ApplyPatchException("Failed to set contents for new file " + newFile.getPath());
    }
    document.setText(getNewFileText());
    FileDocumentManager.getInstance().saveDocument(document);
  }

  public boolean isNewFile() {
    return myHunks.size() == 1 && myHunks.get(0).isNewContent();
  }

  public String getNewFileText() {
    return myHunks.get(0).getText();
  }

  public boolean isDeletedFile() {
    return myHunks.size() == 1 && myHunks.get(0).isDeletedContent();
  }
}
