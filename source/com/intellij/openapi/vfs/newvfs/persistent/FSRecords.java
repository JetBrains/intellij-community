/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.*;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings({"PointlessArithmeticExpression", "HardCodedStringLiteral"})
public class FSRecords implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  private final static int VERSION = 2;

  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTREF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTREF_SIZE = 4;
  private static final int CRC_OFFSET = ATTREF_OFFSET + ATTREF_SIZE;
  private static final int CRC_SIZE = 8;
  private static final int TIMESTAMP_OFFSET = CRC_OFFSET + CRC_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MODCOUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MODCOUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MODCOUNT_OFFSET + MODCOUNT_SIZE;
  private static final int LENGTH_SIZE = 4;

  private final static int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;

  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private static final int HEADER_VERSION_OFFSET = 0;
  private static final int HEADER_FREE_RECORD_OFFSET = 4;
  private static final int HEADER_GLOBAL_MODCOUNT_OFFSET = 8;
  private static final int HEADER_CONNECTION_STATUS_OFFSET = 12;
  private static final int HEADER_SIZE = HEADER_CONNECTION_STATUS_OFFSET + 4;

  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private static final String CHILDREN_ATT = "FsRecords.DIRECTORY_CHILDREN";
  private final static ReentrantLock r = new ReentrantLock();
  private final static ReentrantLock w = r;
  private DbConnection myConnection;

  static {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= RECORD_SIZE;
  }

  private static class DbConnection {
    private static int refCount = 0;
    private static final Object LOCK = new Object();
    private static final TObjectIntHashMap<String> myAttributeIds = new TObjectIntHashMap<String>();

    private static PersistentStringEnumerator myNames;
    private static PagedMemoryMappedFile myAttributes;
    private static MappedFile myRecords;

    public static DbConnection connect() {
      synchronized (LOCK) {
        if (refCount == 0) {
          init();
        }
        refCount++;
      }

      return new DbConnection();
    }

    private static void init() {
      File basePath = new File(PathManager.getSystemPath() + "/caches/");
      basePath.mkdirs();

      final File namesFile = new File(basePath, "names.dat");
      final File attributesFile = new File(basePath, "attrib.dat");
      final File recordsFile = new File(basePath, "records.dat");

      try {
        myNames = new PersistentStringEnumerator(namesFile);
        myAttributes = new PagedMemoryMappedFile(attributesFile);
        myRecords = new MappedFile(recordsFile, 20 * 1024);

        if (myRecords.length() == 0) {
          cleanRecord(0); // Clean header
          cleanRecord(1); // Create root record
          setCurrentVersion();
        }

        if (getVersion() != VERSION) {
          throw new IOException("FS repository version mismatch");
        }

        if (myRecords.getInt(HEADER_CONNECTION_STATUS_OFFSET) != SAFELY_CLOSED_MAGIC) {
          throw new IOException("FS repostiory wasn't safely shut down");
        }
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);

      }
      catch (IOException e) {
        LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
        try {
          closeFiles();

          FileUtil.delete(namesFile);
          FileUtil.delete(attributesFile);
          FileUtil.delete(recordsFile);
        }
        catch (IOException e1) {
          throw new RuntimeException("Can't rebuild filesystem storage ", e1);
        }

        init();
      }
    }

    private static int getVersion() throws IOException {
      return myRecords.getInt(HEADER_VERSION_OFFSET);
    }

    private static void setCurrentVersion() throws IOException {
      myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
    }

    public static void cleanRecord(final int id) throws IOException {
      myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
    }

    public PersistentStringEnumerator getNames() {
      return myNames;
    }

    public PagedMemoryMappedFile getAttributes() {
      return myAttributes;
    }

    public MappedFile getRecords() {
      return myRecords;
    }

    public void dispose() throws IOException {
      synchronized (LOCK) {
        refCount--;
        if (refCount == 0) {
          closeFiles();
        }
      }
    }

    private static void closeFiles() throws IOException {
      if (myNames != null) {
        myNames.close();
        myNames = null;
      }

      if (myAttributes != null) {
        myAttributes.dispose();
        myAttributes = null;
      }

      if (myRecords != null) {
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
        myRecords.close();
        myRecords = null;
      }
    }

    private int getAttributeId(String attId) throws IOException {
      if (myAttributeIds.containsKey(attId)) {
        return myAttributeIds.get(attId);
      }

      int id = myNames.enumerate(attId);
      myAttributeIds.put(attId, id);
      return id;
    }
  }

  public FSRecords() {
  }

  public void connect() throws IOException {
    myConnection = DbConnection.connect();
  }

  public int createRecord() {
    w.lock();
    try {
      final int next = myConnection.getRecords().getInt(HEADER_FREE_RECORD_OFFSET);

      if (next == 0) {
        final int filelength = (int)myConnection.getRecords().length();
        LOG.assertTrue(filelength % RECORD_SIZE == 0);
        int result = filelength / RECORD_SIZE;
        cleanRecord(result);
        return result;
      }
      else {
        myConnection.getRecords().putInt(HEADER_FREE_RECORD_OFFSET, getNextFree(next));
        setNextFree(next, 0);
        return next;
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  public void deleteRecordRecursively(int id) {
    w.lock();
    try {
      incModCount(id);
      doDeleteRecursively(id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  private void doDeleteRecursively(final int id) {
    for (int subrecord : list(id)) {
      doDeleteRecursively(subrecord);
    }

    deleteRecord(id);
  }

  private void deleteRecord(final int id) {
    w.lock();
    try {
      int att_page = myConnection.getRecords().getInt(id * RECORD_SIZE + ATTREF_OFFSET);

      while (att_page != 0) {
        final RandomAccessPagedDataInput page = myConnection.getAttributes().getReader(att_page);
        page.readInt(); // Skip att_id
        final int next = page.readInt();
        page.close();
        myConnection.getAttributes().delete(att_page);
        att_page = next;
      }

      cleanRecord(id);
      addToFreeRecordsList(id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  private void addToFreeRecordsList(int id) throws IOException {
    final int next = myConnection.getRecords().getInt(HEADER_FREE_RECORD_OFFSET);
    setNextFree(id, next);
    myConnection.getRecords().putInt(HEADER_FREE_RECORD_OFFSET, id);
  }

  public int findRootRecord(String rootUrl) throws IOException {
    w.lock();
    try {
      final int root = myConnection.getNames().enumerate(rootUrl);

      final DataInputStream input = readAttribute(1, CHILDREN_ATT);
      int[] names = ArrayUtil.EMPTY_INT_ARRAY;
      int[] ids = ArrayUtil.EMPTY_INT_ARRAY;

      if (input != null) {
        try {
          final int count = input.readInt();
          names = new int[count];
          ids = new int[count];
          for (int i = 0; i < count; i++) {
            final int name = input.readInt();
            final int id = input.readInt();
            if (name == root) {
              return id;
            }

            names[i] = name;
            ids[i] = id;
          }
        }
        finally{
          input.close();
        }
      }

      final DataOutputStream output = writeAttribute(1, CHILDREN_ATT);
      int id;
      try {
        id = createRecord();
        output.writeInt(names.length + 1);
        for (int i = 0; i < names.length; i++) {
          output.writeInt(names[i]);
          output.writeInt(ids[i]);
        }
        output.writeInt(root);
        output.writeInt(id);
      }
      finally {
        output.close();
      }

      return id;
    }
    finally {
      w.unlock();
    }
  }

  public void deleteRootRecord(int id) throws IOException {
    w.lock();
    try {
      final DataInputStream input = readAttribute(1, CHILDREN_ATT);
      assert input != null;
      int count;
      int[] names;
      int[] ids;
      try {
        count = input.readInt();

        names = new int[count];
        ids = new int[count];
        for (int i = 0; i < count; i++) {
          names[i] = input.readInt();
          ids[i] = input.readInt();
        }
      }
      finally {
        input.close();
      }

      final int index = ArrayUtil.find(ids, id);
      assert index >= 0;

      names = ArrayUtil.remove(names, index);
      ids = ArrayUtil.remove(ids, index);

      final DataOutputStream output = writeAttribute(1, CHILDREN_ATT);
      try {
        output.writeInt(count - 1);
        for (int i = 0; i < names.length; i++) {
          output.writeInt(names[i]);
          output.writeInt(ids[i]);
        }
      }
      finally {
        output.close();
      }
    }
    finally {
      w.unlock();
    }
  }

  public int[] list(int id) {
    r.lock();
    try {
      final DataInputStream input = readAttribute(id, CHILDREN_ATT);
      if (input == null) return new int[0];

      final int count = input.readInt();
      final int[] result = new int[count];
      for (int i = 0; i < count; i++) {
        result[i] = input.readInt();
      }
      input.close();
      return result;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void updateList(int id, int[] children) {
    w.lock();
    try {
      final DataOutputStream record = writeAttribute(id, CHILDREN_ATT);
      record.writeInt(children.length);
      for (int child : children) {
        if (child == id) {
          LOG.error("Cyclic parent child relations");
        }
        else {
          record.writeInt(child);
        }
      }
      record.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  private void incModCount(int id) throws IOException {
    final int count = getModCount() + 1;
    myConnection.getRecords().putInt(HEADER_GLOBAL_MODCOUNT_OFFSET, count);

    int parent = id;
    while (parent != 0) {
      setModCount(parent, count);
      parent = getParent(parent);
    }
  }

  public int getModCount()  {
    r.lock();
    try {
      return myConnection.getRecords().getInt(HEADER_GLOBAL_MODCOUNT_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  private void cleanRecord(final int id) throws IOException {
    myConnection.cleanRecord(id);
  }

  public int getParent(int id) {
    r.lock();
    try {
      final int parentId = myConnection.getRecords().getInt(id * RECORD_SIZE + PARENT_OFFSET);
      if (parentId == id) {
        LOG.error("Cyclic parent child relations in the database. id = " + id);
        return 0;
      }

      return parentId;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void setParent(int id, int parent) {
    if (id == parent) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    w.lock();
    try {
      incModCount(id);
      myConnection.getRecords().putInt(id * RECORD_SIZE + PARENT_OFFSET, parent);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      w.unlock();
    }
  }

  private int getNextFree(int id) {
    return getParent(id);
  }

  private void setNextFree(int id, int next) {
    try {
      myConnection.getRecords().putInt(id * RECORD_SIZE + PARENT_OFFSET, next);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String getName(int id) {
    r.lock();
    try {
      final int nameId = myConnection.getRecords().getInt(id * RECORD_SIZE + NAME_OFFSET);
      return nameId != 0 ? myConnection.getNames().valueOf(nameId) : "";
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void setName(int id, String name) {
    w.lock();
    try {
      incModCount(id);
      myConnection.getRecords().putInt(id * RECORD_SIZE + NAME_OFFSET, myConnection.getNames().enumerate(name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  public int getFlags(int id) {
    r.lock();
    try {
      return myConnection.getRecords().getInt(id * RECORD_SIZE + FLAGS_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void setFlags(int id, int flags) {
    w.lock();
    try {
      incModCount(id);
      myConnection.getRecords().putInt(id * RECORD_SIZE + FLAGS_OFFSET, flags);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  public int getLength(int id) {
    r.lock();
    try {
      return myConnection.getRecords().getInt(id * RECORD_SIZE + LENGTH_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void setLength(int id, int len) {
    w.lock();
    try {
      incModCount(id);
      myConnection.getRecords().putInt(id * RECORD_SIZE + LENGTH_OFFSET, len);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  public long getCRC(int id) {
    r.lock();
    try {
      return myConnection.getRecords().getLong(id * RECORD_SIZE + CRC_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void setCRC(int id, long crc) {
    w.lock();
    try {
      incModCount(id);
      myConnection.getRecords().putLong(id * RECORD_SIZE + CRC_OFFSET, crc);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  public long getTimestamp(int id) {
    r.lock();
    try {
      return myConnection.getRecords().getLong(id * RECORD_SIZE + TIMESTAMP_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  public void setTimestamp(int id, long value) {
    w.lock();
    try {
      incModCount(id);
      myConnection.getRecords().putLong(id * RECORD_SIZE + TIMESTAMP_OFFSET, value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }

  public int getModCount(int id) {
    r.lock();
    try {
      return myConnection.getRecords().getInt(id * RECORD_SIZE + MODCOUNT_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      r.unlock();
    }
  }

  private void setModCount(int id, int value) throws IOException {
    myConnection.getRecords().putInt(id * RECORD_SIZE + MODCOUNT_OFFSET, value);
  }

  @Nullable
  public DataInputStream readAttribute(int id, String attId) {
    try {
      r.lock();
      int encodedAttId = myConnection.getAttributeId(attId);
      int att_page = myConnection.getRecords().getInt(id * RECORD_SIZE + ATTREF_OFFSET);
      while (att_page != 0) {
        final RandomAccessPagedDataInput page = myConnection.getAttributes().getReader(att_page);
        final int attIdOnPage = page.readInt();
        final int next = page.readInt();
        if (attIdOnPage == encodedAttId) {
          return new DataInputStream(page) {
            boolean closed = false;
            public void close() throws IOException {
              if (!closed) {
                closed = true;
                super.close();
                r.unlock();
              }
            }

            protected void finalize() throws Throwable {
              if (!closed) {
                w.unlock();
              }

              super.finalize();
            }
          };
        }
        att_page = next;
        page.close();
      }

      r.unlock();
      return null;
    }
    catch (IOException e) {
      r.unlock();
      throw new RuntimeException(e);
    }
  }

  private DataOutputStream findPageToWrite(int id, final int encodedAttId, final int headPage) throws IOException {
    final RecordDataOutput result;
    incModCount(id);

    int att_page = headPage;

    while (att_page != 0) {
      int curPage = att_page;
      final RandomAccessPagedDataInput page = myConnection.getAttributes().getReader(att_page);
      final int attIdOnPage = page.readInt();
      final int next = page.readInt();
      if (attIdOnPage == encodedAttId) {
        page.close();
        result = myConnection.getAttributes().getWriter(curPage);
        result.writeInt(encodedAttId);
        result.writeInt(next);

        return (DataOutputStream)result;
      }

      att_page = next;
      page.close();
    }

    result = myConnection.getAttributes().createRecord();
    result.writeInt(encodedAttId);
    result.writeInt(headPage);

    myConnection.getRecords().putInt(id * RECORD_SIZE + ATTREF_OFFSET, result.getRecordId());
    return (DataOutputStream)result;
  }

  @NotNull
  public DataOutputStream writeAttribute(final int id, final String attId) {
    w.lock();
    DataOutputStream result;
    try {
      final int encodedAttId = myConnection.getAttributeId(attId);
      final int headPage = myConnection.getRecords().getInt(id * RECORD_SIZE + ATTREF_OFFSET);
      result = findPageToWrite(id, encodedAttId, headPage);
      return new DataOutputStream(result) {
        boolean closed = false;
        public void close() throws IOException {
          if (!closed) {
            closed = true;
            super.close();
            w.unlock();
          }
        }

        protected void finalize() throws Throwable {
          if (!closed) {
            w.unlock();
          }

          super.finalize();
        }
      };
    }
    catch (IOException e) {
      w.unlock();
      throw new RuntimeException(e);
    }
  }

  public void disposeAndDeleteFiles() {
    w.lock();
    try {
      dispose();
    }
    finally {
      w.unlock();
    }
  }

  public void dispose() {
    w.lock();
    try {
      if (myConnection != null) {
        myConnection.dispose();
        myConnection = null;
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally{
      w.unlock();
    }
  }
}