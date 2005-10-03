package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

public class StorageProperty extends AbstractProperty<Storage> {
  private final String myName;

  public StorageProperty(@NonNls String name) {
    myName = name;
  }

  public Storage getDefault(AbstractProperty.AbstractPropertyContainer container) {
    Storage.MapStorage storage = new Storage.MapStorage();
    set(container, storage);
    return storage;
  }

  public Storage copy(Storage storage) {
    if (!(storage instanceof Storage.MapStorage))
      throw new UnsupportedOperationException(storage.getClass().getName());
    Iterator<String> keys = ((Storage.MapStorage)storage).getKeys();
    Storage.MapStorage copy = new Storage.MapStorage();
    while (keys.hasNext()) {
      String key = keys.next();
      copy.put(key, storage.get(key));
    }
    return copy;
  }

  public String getName() {
    return myName;
  }
}
