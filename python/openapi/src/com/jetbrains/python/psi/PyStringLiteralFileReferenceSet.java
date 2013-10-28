/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class PyStringLiteralFileReferenceSet extends RootFileReferenceSet {
  private final PyStringLiteralExpression myStringLiteralExpression;


  public PyStringLiteralFileReferenceSet(@NotNull PyStringLiteralExpression element) {
    this(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public PyStringLiteralFileReferenceSet(@NotNull PyStringLiteralExpression element, boolean caseSensitive) {
    this(element.getStringValue(), element, element.getStringValueTextRange().getStartOffset(), null, caseSensitive, true, new FileType[0]);
  }

  public PyStringLiteralFileReferenceSet(@NotNull String str,
                                         @NotNull PyStringLiteralExpression element,
                                         int startInElement,
                                         PsiReferenceProvider provider,
                                         boolean caseSensitive, boolean endingSlashNotAllowed, @Nullable FileType[] suitableFileTypes) {
    super(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed,
          suitableFileTypes);
    myStringLiteralExpression = element;
    reparse();
  }

  @Override
  protected void reparse() {
    //noinspection ConstantConditions
    if (myStringLiteralExpression != null) {
      MyTextRangeConsumer textRangeConsumer = new MyTextRangeConsumer(this);

      myStringLiteralExpression.iterateCharacterRanges(textRangeConsumer);
      textRangeConsumer.finish();

      List<FileReference> referencesList = textRangeConsumer.myReferenceList;

      myReferences = referencesList.toArray(new FileReference[referencesList.size()]);
    }
  }

  private static class MyTextRangeConsumer implements PyStringLiteralExpression.TextRangeConsumer {
    private final StringBuilder myItem = new StringBuilder();
    private int myStartOffset = -1;
    private int myIndex = 0;
    private int myEndOffset = -1;
    private final FileReferenceSet myFileReferenceSet;


    private final List<FileReference> myReferenceList = new ArrayList<FileReference>();

    private MyTextRangeConsumer(FileReferenceSet set) {
      myFileReferenceSet = set;
    }

    @Override
    public boolean process(int startOffset, int endOffset, String value) {
      if ("\\".equals(value) || "/".equals(value)) {
        addReference(startOffset);
      }
      else {
        if (myStartOffset == -1) {
          myStartOffset = startOffset;
        }
        myEndOffset = endOffset;
        myItem.append(value);
      }
      return true;
    }

    private void addReference(int startOffset) {
      if (myStartOffset != -1) {
        final FileReference ref = myFileReferenceSet.createFileReference(
          new TextRange(myStartOffset, startOffset),
          myIndex++,
          myItem.toString());
        myReferenceList.add(ref);
        myStartOffset = -1;
        myItem.setLength(0);
      }
    }


    public void finish() {
      addReference(myEndOffset);
    }
  }
}
