package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.LimitedPool;
import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2007
 */
public class PersistentHashMap<Key, Value> extends PersistentEnumerator<Key>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.PersistentHashMap");

  private PersistentHashMapValueStorage myValueStorage;
  private final DataExternalizer<Value> myValueExternalizer;
  private static final long NULL_ADDR = 0;
  private static final int NULL_SIZE = 0;

  @NonNls
  public static final String DATA_FILE_EXTENSION = ".values";
  private File myFile;
  private int myGarbageSize;

  private static class AppendStream extends DataOutputStream {
    private AppendStream() {
      super(new ByteArrayOutputStream());
    }

    public void writeTo(OutputStream stream) throws IOException {
      ((ByteArrayOutputStream)out).writeTo(stream);
    }

    public void reset() {
      ((ByteArrayOutputStream)out).reset();
    }

    public byte[] toByteArray() {
      return ((ByteArrayOutputStream)out).toByteArray();
    }
  }

  private final LimitedPool<AppendStream> myStreamPool = new LimitedPool<AppendStream>(10, new LimitedPool.ObjectFactory<AppendStream>() {
    public AppendStream create() {
      return new AppendStream();
    }

    public void cleanup(final AppendStream appendStream) {
      appendStream.reset();
    }
  });

  private final SLRUCache<Key, AppendStream> myAppendCache = new SLRUCache<Key, AppendStream>(16 * 1024, 4 * 1024) {
    @NotNull
    public AppendStream createValue(final Key key) {
      return myStreamPool.alloc();
    }

    protected void onDropFromCache(final Key key, final AppendStream value) {
      try {
        final int id = enumerate(key);
        HeaderRecord headerRecord = readValueId(id);

        final byte[] bytes = value.toByteArray();

        headerRecord.size += bytes.length;
        headerRecord.address = myValueStorage.appendBytes(bytes, headerRecord.address);

        updateValueId(id, headerRecord);

        myStreamPool.recycle(value);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public PersistentHashMap(final File file, PersistentEnumerator.DataDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer) throws IOException {
    this(file, keyDescriptor, valueExternalizer, 1024 * 4);
  }
  
  public PersistentHashMap(final File file, PersistentEnumerator.DataDescriptor<Key> keyDescriptor, DataExternalizer<Value> valueExternalizer, final int initialSize) throws IOException {
    super(checkDataFile(file), new DescriptorWrapper<Key>(keyDescriptor), initialSize);
    myFile = file;
    myValueExternalizer = valueExternalizer;
    myValueStorage = PersistentHashMapValueStorage.create(getDataFile(myFile).getPath());
    myGarbageSize = getMetaData();

    if (makesSenceToCompact()) {
      compact();
    }
  }
  
  private boolean makesSenceToCompact() {
    final long filesize = myFile.length();
    return filesize > 5 * 1024 * 1024 && myGarbageSize * 2 > filesize; // file is longer than 5MB and more than 50% of data is garbage
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
    myAppendCache.remove(key);
    
    final int id = enumerate(key);
    AppendStream record = new AppendStream();
    myValueExternalizer.save(record, value);
    byte[] bytes = record.toByteArray();

    HeaderRecord header = readValueId(id);
    if (header != null) {
      myGarbageSize += header.size;
    }
    else {
      header = new HeaderRecord();
    }

    header.size = bytes.length;
    header.address = myValueStorage.appendBytes(bytes, 0);

    updateValueId(id, header);
  }

  public static interface ValueDataAppender {
    void append(DataOutput out) throws IOException;
  }
  
  public synchronized void appendData(Key key, ValueDataAppender appender) throws IOException {
    appender.append(myAppendCache.get(key));
  }

  public synchronized Collection<Key> allKeys() throws IOException {
    myAppendCache.clear();
    return getAllDataObjects(new DataFilter() {
      public boolean accept(final int id) {
        try {
          return readValueId(id).address != NULL_ADDR;
        }
        catch (IOException ignored) {
        }
        return true;
      }
    });
  }

  public synchronized Value get(Key key) throws IOException {
    myAppendCache.remove(key);
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return null;
    }
    final HeaderRecord header = readValueId(id);
    if (header.address == NULL_ID) {
      return null;
    }

    byte[] data = new byte[header.size];
    long newAddress = myValueStorage.readBytes(header.address, data);
    if (newAddress != header.address) {
      header.address = newAddress;
      updateValueId(id, header);
      myGarbageSize += header.size;
    }

    final DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
    try {
      return myValueExternalizer.read(input);
    }
    finally {
      input.close();
    }
  }
  
  public synchronized void remove(Key key) throws IOException {
    myAppendCache.remove(key);
    final int id = tryEnumerate(key);
    if (id == NULL_ID) {
      return;
    }

    final HeaderRecord record = readValueId(id);
    if (record != null) {
      myGarbageSize += record.size;
    }

    updateValueId(id, new HeaderRecord());
  }

  public synchronized void force() {
    myAppendCache.clear();
    myValueStorage.force();
    try {
      putMetaData(myGarbageSize);
    }
    catch (IOException e) {
      // ignore
    }

    super.force();
  }

  public synchronized void close() throws IOException {
    myAppendCache.clear();
    super.close();
    myValueStorage.dispose();
  }

  public synchronized void compact() throws IOException {
    long now = System.currentTimeMillis();
    final String newPath = getDataFile(myFile).getPath() + ".new";
    final PersistentHashMapValueStorage newStorage = PersistentHashMapValueStorage.create(newPath);
    myValueStorage.switchToCompactionMode();
    
    traverseAllRecords(new RecordsProcessor() {
      public void process(final int keyId) throws IOException {
        final HeaderRecord record = readValueId(keyId);
        if (record.address != NULL_ADDR) {
          byte[] bytes = new byte[record.size];
          myValueStorage.readBytes(record.address, bytes);
          record.address = newStorage.appendBytes(bytes, 0);
          updateValueId(keyId, record);
        }
      }
    });

    myValueStorage.dispose();
    newStorage.dispose();

    new File(newPath).renameTo(getDataFile(myFile));

    myValueStorage = PersistentHashMapValueStorage.create(getDataFile(myFile).getPath());
    LOG.info("Compacted " + myFile.getPath() + " in " + (System.currentTimeMillis() - now) + "ms.");
  }

  private HeaderRecord readValueId(final int keyId) throws IOException {
    HeaderRecord result = new HeaderRecord();
    result.address = myStorage.getLong(keyId + DATA_OFFSET);
    result.size = myStorage.getInt(keyId + DATA_OFFSET + 8);
    return result;
  }

  private void updateValueId(final int keyId, HeaderRecord value) throws IOException {
    myStorage.putLong(keyId + DATA_OFFSET, value.address);
    myStorage.putInt(keyId + DATA_OFFSET + 8, value.size);
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
      out.writeLong(NULL_ADDR);
      out.writeInt(NULL_SIZE);

      myKeyDescriptor.save(out, value);
    }

    public T read(final DataInput in) throws IOException {
      in.skipBytes(8 + 4);
      return myKeyDescriptor.read(in);
    }
  }

  private static class HeaderRecord {
    long address;
    int size;
  }
}
