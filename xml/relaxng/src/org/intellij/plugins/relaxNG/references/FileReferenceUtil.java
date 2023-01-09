/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.references;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Condition;
import com.intellij.patterns.PsiFilePattern;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.XmlPatterns.*;

public final class FileReferenceUtil {

  public static PsiReference[] restrict(FileReferenceSet set, final Condition<? super PsiFile> cond) {
    return restrict(set, cond, null);
  }

  public static PsiReference[] restrict(FileReferenceSet set, final Condition<? super PsiFile> cond, final Boolean soft) {
    final FileReference[] references = set.getAllReferences();

    return ContainerUtil.map2Array(references, PsiReference.class, (NotNullFunction<FileReference, PsiReference>)fileReference -> new MyFileReference(fileReference, cond, soft));
  }

  public static Condition<PsiFile> byType(FileType instance) {
    return new TypeCondition(instance);
  }

  public static Condition<PsiFile> byNamespace(String ns) {
    return new PatternCondition(xmlFile().withRootTag(xmlTag().withNamespace(string().equalTo(ns))));
  }

  private static class TypeCondition implements Condition<PsiFile> {
    private final FileType myType;

    TypeCondition(FileType type) {
      myType = type;
    }

    @Override
    public boolean value(PsiFile file) {
      return file.getFileType() == myType;
    }
  }

  private static class PatternCondition implements Condition<PsiFile> {
    private final PsiFilePattern myPattern;

    PatternCondition(PsiFilePattern pattern) {
      myPattern = pattern;
    }

    @Override
    public boolean value(PsiFile o) {
      return myPattern.accepts(o);
    }
  }

  private static class MyFileReference extends FileReference {
    private final Condition<? super PsiFile> myCond;
    private final Boolean mySoft;

    MyFileReference(FileReference fileReference, Condition<? super PsiFile> cond, @Nullable Boolean soft) {
      super(fileReference.getFileReferenceSet(), fileReference.getRangeInElement(), fileReference.getIndex(), fileReference.getCanonicalText());
      myCond = cond;
      mySoft = soft;
    }

    @Override
    public boolean isSoft() {
      return mySoft == null ? super.isSoft() : mySoft;
    }

    @Override
    public PsiFileSystemItem resolve() {
      final PsiFileSystemItem result = super.resolve();
      if (result instanceof PsiFile) {
        if (!myCond.value((PsiFile)result)) {
          return null;
        }
      }
      return result;
    }

    @Override
    public Object @NotNull [] getVariants() {
      final Object[] variants = super.getVariants();
      return ContainerUtil.findAll(variants, o -> {
        /*if (o instanceof CandidateInfo) {
          o = ((CandidateInfo)o).getElement();
        }*/
        return match(o, myCond);
      }).toArray();
    }

    private static boolean match(Object o, Condition<? super PsiFile> cond) {
      return !(o instanceof PsiFileSystemItem) ||
              ((PsiFileSystemItem)o).isDirectory() ||
              (o instanceof PsiFile && cond.value((PsiFile)o));
    }
  }
}
