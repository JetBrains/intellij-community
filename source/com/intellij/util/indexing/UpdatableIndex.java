package com.intellij.util.indexing;

import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface UpdatableIndex<Key, Value, Input> extends AbstractIndex<Key,Value> {

  void removeData(Key key) throws StorageException;

  void update(int inputId, @Nullable Input content, @Nullable Input oldContent) throws StorageException;
}
