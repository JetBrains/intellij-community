package com.intellij.xml.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.NullableFunction;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class IndexedRelevantResource<K, V> implements Comparable<IndexedRelevantResource> {

  public static <K, V> List<IndexedRelevantResource<K, V>> getSortedResources(ID<K, V> indexId, final K key, @NotNull final Module module) {

    final ArrayList<IndexedRelevantResource<K, V>> resources = new ArrayList<IndexedRelevantResource<K, V>>();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    FileBasedIndex.getInstance().processValues(indexId, key, null, new FileBasedIndex.ValueProcessor<V>() {
      public boolean process(VirtualFile file, V value) {
        ResourceRelevance relevance = ResourceRelevance.getRelevance(file, module, fileIndex);
        if (relevance != ResourceRelevance.NONE) {
          resources.add(new IndexedRelevantResource<K, V>(file, key, value, relevance));
        }
        return true;
      }
    }, GlobalSearchScope.allScope(module.getProject()));
    Collections.sort(resources);
    return resources;
  }

  public static <K, V> List<IndexedRelevantResource<K, V>> getAllResources(ID<K, V> indexId,
                                                                           @NotNull final Module module,
                                                                           @Nullable NullableFunction<List<IndexedRelevantResource<K, V>>, IndexedRelevantResource<K, V>> chooser) {
    ArrayList<IndexedRelevantResource<K, V>> all = new ArrayList<IndexedRelevantResource<K, V>>();
    Collection<K> allKeys = FileBasedIndex.getInstance().getAllKeys(indexId, module.getProject());
    for (K key : allKeys) {
      List<IndexedRelevantResource<K, V>> resources = getSortedResources(indexId, key, module);
      if (!resources.isEmpty()) {
        if (chooser == null) {
          all.add(resources.get(0));
        }
        else {
          IndexedRelevantResource<K, V> resource = chooser.fun(resources);
          if (resource != null) {
            all.add(resource);
          }
        }
      }
    }
    return all;
  }

  private final VirtualFile myFile;
  private final K myKey;
  private final V myValue;
  private final ResourceRelevance myRelevance;

  public IndexedRelevantResource(VirtualFile file, K key, V value, ResourceRelevance relevance) {
    myFile = file;
    myKey = key;
    myValue = value;
    myRelevance = relevance;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public V getValue() {
    return myValue;
  }

  public ResourceRelevance getRelevance() {
    return myRelevance;
  }

  public int compareTo(IndexedRelevantResource o) {
    return myRelevance.compareTo(o.getRelevance());
  }

  public K getKey() {
    return myKey;
  }

  @Override
  public String toString() {
    return "IndexedRelevantResource{" +
           "myRelevance=" +
           myRelevance +
           ", myKey=" +
           myKey +
           ", myValue=" +
           myValue +
           ", myFile=" +
           myFile +
           '}';
  }
}
