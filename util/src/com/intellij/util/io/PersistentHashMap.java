package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.storage.Storage;
import org.jetbrains.annotations.NonNls;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2007
 */
public class PersistentHashMap<Key, Value> extends PersistentEnumerator<Key>{
  private Storage myValueStorage;
  private final DataExternalizer<Value> myValueExternalizer;
  @NonNls public static final String DATA_FILE_EXTENSION = ".values";
  public PersistentHashMap(final File file, PersistentEnumerator.DataDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer) throws IOException {
    this(file, keyDescriptor, valueExternalizer, 1024 * 4);
  }
  
  public PersistentHashMap(final File file, PersistentEnumerator.DataDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer, final int initialSize) throws IOException {
    super(checkDataFile(file), new DescriptorWrapper<Key>(keyDescriptor), initialSize);
    myValueExternalizer = valueExternalizer;
    myValueStorage = Storage.create(getDataFile(file).getPath());
  }

  private static File checkDataFile(final File file) throws IOException{
    final File dataFile = getDataFile(file);
    if (!file.exists()) {
      final File[] files = dataFile.getParentFile().listFiles(new FileFilter() {
        public boolean accept(final File pathname) {
          return pathname.getName().startsWith(dataFile.getName());
        }
      });
      for (File f : files) {
        FileUtil.delete(f);
      }
      
    }
    return file;
  }

  private static File getDataFile(final File file) {
    return new File(file.getParentFile(), file.getName() + DATA_FILE_EXTENSION);
  }

  public synchronized void put(Key key, Value value) throws IOException {
    final int id = enumerate(key);
    int valueId = readValueId(id);
    if (valueId == NULL_ID) {
      valueId = myValueStorage.createNewRecord();
      updateValueId(id, valueId);
    }
    final Storage.StorageDataOutput record = myValueStorage.writeStream(valueId);
    try {
      myValueExternalizer.save(record, value);
    }
    finally {
      record.close();
    }
  }

  public static interface ValueDataAppender {
    void append(DataOutput out) throws IOException;
  }
  
  public synchronized void appendData(Key key, ValueDataAppender appender) throws IOException {
    final int id = enumerate(key);
    int valueId = readValueId(id);
    if (valueId == NULL_ID) {
      valueId = myValueStorage.createNewRecord();
      updateValueId(id, valueId);
    }
    final Storage.AppenderStream record = myValueStorage.appendStream(valueId);
    try {
      appender.append(record);
    }
    finally {
      record.close();
    }
  }

  
  public synchronized Value get(Key key) throws IOException {
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return null;
    }
    final int valueId = readValueId(id);
    if (valueId == NULL_ID) {
      return null;
    }
    final DataInputStream input = myValueStorage.readStream(valueId);
    try {
      return myValueExternalizer.read(input);
    }
    finally {
      input.close();
    }
  }
  
  public synchronized void remove(Key key) throws IOException {
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return;
    }
    final int valueId = readValueId(id);
    if (valueId == NULL_ID) {
      return;
    }
    updateValueId(id, NULL_ID);
    myValueStorage.deleteRecord(valueId);
  }

  public void close() throws IOException {
    super.close();
    myValueStorage.dispose();
  }

  private int readValueId(final int keyId) throws IOException {
    return myStorage.getInt(keyId + DATA_OFFSET);
  }

  private void updateValueId(final int keyId, int value) throws IOException {
    myStorage.putInt(keyId + DATA_OFFSET, value);
  }

  private static final class DescriptorWrapper<T> implements PersistentEnumerator.DataDescriptor<T>{
    private final PersistentEnumerator.DataDescriptor<T> myKeyDescriptor;

    public DescriptorWrapper(PersistentEnumerator.DataDescriptor<T> keyDescriptor) {
      myKeyDescriptor = keyDescriptor;
    }

    public int getHashCode(final T value) {
      return myKeyDescriptor.getHashCode(value);
    }

    public boolean isEqual(final T val1, final T val2) {
      return myKeyDescriptor.isEqual(val1, val2);
    }

    public void save(final DataOutput out, final T value) throws IOException {
      out.writeInt(NULL_ID);
      myKeyDescriptor.save(out, value);
    }

    public T read(final DataInput in) throws IOException {
      in.skipBytes(4);
      return myKeyDescriptor.read(in);
    }
  }
  
  
}
