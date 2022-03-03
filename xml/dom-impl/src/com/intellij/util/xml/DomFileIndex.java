// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Use {@link DomService#getDomFileCandidates}.
 */
@ApiStatus.Internal
public final class DomFileIndex extends ScalarIndexExtension<DomFileIndex.DomIndexKey> {
  private static final ID<DomIndexKey, Void> INDEX_ID = ID.create("DomFileIndex");
  private static final String NULL_NAMESPACE = "-NULL-";

  @Override
  @NotNull
  public ID<DomIndexKey, Void> getName() {
    return INDEX_ID;
  }

  @NotNull
  public static Collection<VirtualFile> findFiles(@NotNull String rootTagName,
                                                  @Nullable String namespace,
                                                  @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(INDEX_ID, new DomIndexKey(rootTagName, ObjectUtils.notNull(namespace, NULL_NAMESPACE)), scope);
  }

  @Override
  @NotNull
  public DataIndexer<DomIndexKey, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      @NotNull
      public Map<DomIndexKey, Void> map(@NotNull FileContent inputData) {
        XmlFileHeader header = NanoXmlUtil.parseHeader(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        String rootTagName = header.getRootTagLocalName();
        if (rootTagName == null) {
          return Collections.emptyMap();
        }

        String[] namespaces = new String[]{header.getPublicId(), header.getSystemId(), header.getRootTagNamespace(), NULL_NAMESPACE};
        Map<DomIndexKey, Void> set = new HashMap<>(namespaces.length);
        for (String t : namespaces) {
          if (t != null) {
            set.put(new DomIndexKey(rootTagName, t), null);
          }
        }
        return set;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<DomIndexKey> getKeyDescriptor() {
    return new DomIndexKeyDescriptor();
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  public static class DomIndexKey {
    @NotNull
    private final String myRootTagName;
    @NotNull
    private final String myNamespace;

    private DomIndexKey(@NotNull String rootTagName, @NotNull String namespace) {
      myRootTagName = rootTagName;
      myNamespace = namespace;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DomIndexKey key = (DomIndexKey)o;
      return myRootTagName.equals(key.myRootTagName) && myNamespace.equals(key.myNamespace);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRootTagName, myNamespace);
    }

    @Override
    public String toString() {
      return "DomIndexKey{" +
             "myRootTagName='" + myRootTagName + '\'' +
             ", myNamespace='" + myNamespace + '\'' +
             '}';
    }
  }

  private static class DomIndexKeyDescriptor implements KeyDescriptor<DomIndexKey> {
    @Override
    public boolean isEqual(DomIndexKey val1, DomIndexKey val2) {
      return val1.equals(val2);
    }

    @Override
    public DomIndexKey read(@NotNull DataInput in) throws IOException {
      return new DomIndexKey(EnumeratorStringDescriptor.INSTANCE.read(in), EnumeratorStringDescriptor.INSTANCE.read(in));
    }

    @Override
    public int getHashCode(DomIndexKey value) {
      return value.hashCode();
    }

    @Override
    public void save(@NotNull DataOutput out, DomIndexKey value) throws IOException {
      EnumeratorStringDescriptor.INSTANCE.save(out, value.myRootTagName);
      EnumeratorStringDescriptor.INSTANCE.save(out, value.myNamespace);
    }
  }

}
