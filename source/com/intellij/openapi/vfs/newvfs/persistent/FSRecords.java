/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.MappedFile;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.io.storage.Storage;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"PointlessArithmeticExpression", "HardCodedStringLiteral"})
public class FSRecords implements Disposable, Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  private final static int VERSION = 5;

  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTREF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTREF_SIZE = 4;
  private static final int TIMESTAMP_OFFSET = ATTREF_OFFSET + ATTREF_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MODCOUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MODCOUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MODCOUNT_OFFSET + MODCOUNT_SIZE;
  private static final int LENGTH_SIZE = 8;

  private final static int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;

  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private static final int HEADER_VERSION_OFFSET = 0;
  private static final int HEADER_FREE_RECORD_OFFSET = 4;
  private static final int HEADER_GLOBAL_MODCOUNT_OFFSET = 8;
  private static final int HEADER_CONNECTION_STATUS_OFFSET = 12;
  private static final int HEADER_SIZE = HEADER_CONNECTION_STATUS_OFFSET + 4;

  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;
  private static final int CORRUPTED_MAGIC = 0xabcf7f7f;

  private static final String CHILDREN_ATT = "FsRecords.DIRECTORY_CHILDREN";
  private final static Object lock = new Object();
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
    private static Storage myAttributes;
    private static MappedFile myRecords;

    private static boolean myDirty = false;
    private static ScheduledFuture<?> myFlashingFuture;
    private static boolean myCorrupted = false;

    public static DbConnection connect() {
      synchronized (LOCK) {
        if (refCount == 0) {
          init();
          setupFlashing();
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
        myAttributes = Storage.create(attributesFile.getCanonicalPath());
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
        markDirty();
      }
      catch (IOException e) {
        LOG.info("Filesystem storage is corrupted or does not exist. [Re]Building. Reason: " + e.getMessage());
        try {
          closeFiles();

          boolean deleted =
            FileUtil.delete(namesFile) && Storage.deleteFiles(attributesFile.getCanonicalPath()) && FileUtil.delete(recordsFile);

          if (!deleted) {
            throw new IOException("Cannot delete filesystem storage files");
          }
        }
        catch (IOException e1) {
          throw new RuntimeException("Can't rebuild filesystem storage ", e1);
        }

        init();
      }
    }

    private static void markDirty() throws IOException {
      if (!myDirty) {
        myDirty = true;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);
      }
    }

    private static void setupFlashing() {
      myFlashingFuture = JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
        public void run() {
          force();
        }
      }, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    public static void force() {
      synchronized (lock) {
        try {
          markClean();
        }
        catch (IOException e) {
          // Ignore
        }
        myNames.force();
        myAttributes.force();
        myRecords.force();
      }
    }

    public static boolean isDirty() {
      return myDirty || myNames.isDirty() || myAttributes.isDirty() || myRecords.isDirty();
    }


    private static int getVersion() throws IOException {
      final int storageVersion = myAttributes.getVersion();
      final int recordsVersion = myRecords.getInt(HEADER_VERSION_OFFSET);
      if (storageVersion != recordsVersion) return -1;

      return recordsVersion;
    }

    private static void setCurrentVersion() throws IOException {
      myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
      myAttributes.setVersion(VERSION);
      myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
    }

    public static void cleanRecord(final int id) throws IOException {
      myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
    }

    public PersistentStringEnumerator getNames() {
      return myNames;
    }

    public Storage getAttributes() {
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
      if (myFlashingFuture != null) {
        myFlashingFuture.cancel(false);
        myFlashingFuture = null;
      }

      if (myNames != null) {
        myNames.close();
        myNames = null;
      }

      if (myAttributes != null) {
        myAttributes.dispose();
        myAttributes = null;
      }

      if (myRecords != null) {
        markClean();
        myRecords.close();
        myRecords = null;
      }
    }

    private static void markClean() throws IOException {
      if (myDirty) {
        myDirty = false;
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, myCorrupted ? CORRUPTED_MAGIC : SAFELY_CLOSED_MAGIC);
      }
    }

    private static int getAttributeId(String attId) throws IOException {
      if (myAttributeIds.containsKey(attId)) {
        return myAttributeIds.get(attId);
      }

      int id = myNames.enumerate(attId);
      myAttributeIds.put(attId, id);
      return id;
    }

    private static RuntimeException handleError(final IOException e) {
      if (!myCorrupted) {
        myCorrupted = true;
        force();
      }

      return new RuntimeException(e);
    }
  }

  public FSRecords() {
  }

  public void connect() throws IOException {
    myConnection = DbConnection.connect();
  }

  private MappedFile getRecords() {
    return myConnection.getRecords();
  }

  private Storage getAttributes() {
    return myConnection.getAttributes();
  }

  private PersistentStringEnumerator getNames() {
    return myConnection.getNames();
  }

  public int createRecord() {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        final int next = getRecords().getInt(HEADER_FREE_RECORD_OFFSET);

        if (next == 0) {
          final int filelength = (int)getRecords().length();
          LOG.assertTrue(filelength % RECORD_SIZE == 0);
          int result = filelength / RECORD_SIZE;
          DbConnection.cleanRecord(result);
          return result;
        }
        else {
          getRecords().putInt(HEADER_FREE_RECORD_OFFSET, getNextFree(next));
          setNextFree(next, 0);
          return next;
        }
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void deleteRecordRecursively(int id) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        incModCount(id);
        doDeleteRecursively(id);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  private void doDeleteRecursively(final int id) {
    for (int subrecord : list(id)) {
      doDeleteRecursively(subrecord);
    }

    deleteRecord(id);
  }

  private void deleteRecord(final int id) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        int att_page = getRecords().getInt(id * RECORD_SIZE + ATTREF_OFFSET);

        while (att_page != 0) {
          final DataInputStream page = getAttributes().readStream(att_page);
          page.readInt(); // Skip att_id
          final int next = page.readInt();
          page.close();
          getAttributes().deleteRecord(att_page);
          att_page = next;
        }

        DbConnection.cleanRecord(id);
        addToFreeRecordsList(id);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  private void addToFreeRecordsList(int id) throws IOException {
    final int next = getRecords().getInt(HEADER_FREE_RECORD_OFFSET);
    setNextFree(id, next);
    getRecords().putInt(HEADER_FREE_RECORD_OFFSET, id);
  }

  public int[] listRoots() throws IOException {
    synchronized (lock) {
      DbConnection.markDirty();
      final DataInputStream input = readAttribute(1, CHILDREN_ATT);
      if (input == null) return ArrayUtil.EMPTY_INT_ARRAY;

      int[] result;
      try {
        final int count = input.readInt();
        result = new int[count];
        for (int i = 0; i < count; i++) {
          input.readInt(); // Name
          result[i] = input.readInt(); // Id
        }
      }
      finally {
        input.close();
      }

      return result;
    }
  }

  public void force() {
    DbConnection.force();
  }

  public boolean isDirty() {
    return DbConnection.isDirty();
  }

  public int findRootRecord(String rootUrl) throws IOException {
    synchronized (lock) {
      DbConnection.markDirty();
      final int root = getNames().enumerate(rootUrl);

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
        finally {
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
  }

  public void deleteRootRecord(int id) throws IOException {
    synchronized (lock) {
      DbConnection.markDirty();
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
  }

  public int[] list(int id) {
    synchronized (lock) {
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
        throw DbConnection.handleError(e);
      }
    }
  }

  public void updateList(int id, int[] children) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
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
        throw DbConnection.handleError(e);
      }
    }
  }

  private void incModCount(int id) throws IOException {
    final int count = getModCount() + 1;
    getRecords().putInt(HEADER_GLOBAL_MODCOUNT_OFFSET, count);

    int parent = id;
    while (parent != 0) {
      setModCount(parent, count);
      parent = getParent(parent);
    }
  }

  public int getModCount() {
    synchronized (lock) {
      try {
        return getRecords().getInt(HEADER_GLOBAL_MODCOUNT_OFFSET);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public int getParent(int id) {
    synchronized (lock) {
      try {
        final int parentId = getRecords().getInt(id * RECORD_SIZE + PARENT_OFFSET);
        if (parentId == id) {
          LOG.error("Cyclic parent child relations in the database. id = " + id);
          return 0;
        }

        return parentId;
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void setParent(int id, int parent) {
    if (id == parent) {
      LOG.error("Cyclic parent/child relations");
      return;
    }

    synchronized (lock) {
      try {
        DbConnection.markDirty();
        incModCount(id);
        getRecords().putInt(id * RECORD_SIZE + PARENT_OFFSET, parent);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  private int getNextFree(int id) {
    return getParent(id);
  }

  private void setNextFree(int id, int next) {
    try {
      getRecords().putInt(id * RECORD_SIZE + PARENT_OFFSET, next);
    }
    catch (IOException e) {
      throw DbConnection.handleError(e);
    }
  }

  public String getName(int id) {
    synchronized (lock) {
      try {
        final int nameId = getRecords().getInt(id * RECORD_SIZE + NAME_OFFSET);
        return nameId != 0 ? getNames().valueOf(nameId) : "";
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void setName(int id, String name) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        incModCount(id);
        getRecords().putInt(id * RECORD_SIZE + NAME_OFFSET, getNames().enumerate(name));
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public int getFlags(int id) {
    synchronized (lock) {
      try {
        return getRecords().getInt(id * RECORD_SIZE + FLAGS_OFFSET);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void setFlags(int id, int flags) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        incModCount(id);
        getRecords().putInt(id * RECORD_SIZE + FLAGS_OFFSET, flags);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public long getLength(int id) {
    synchronized (lock) {
      try {
        return getRecords().getLong(id * RECORD_SIZE + LENGTH_OFFSET);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void setLength(int id, long len) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        incModCount(id);
        getRecords().putLong(id * RECORD_SIZE + LENGTH_OFFSET, len);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public long getTimestamp(int id) {
    synchronized (lock) {
      try {
        return getRecords().getLong(id * RECORD_SIZE + TIMESTAMP_OFFSET);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void setTimestamp(int id, long value) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        incModCount(id);
        getRecords().putLong(id * RECORD_SIZE + TIMESTAMP_OFFSET, value);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public int getModCount(int id) {
    synchronized (lock) {
      try {
        return getRecords().getInt(id * RECORD_SIZE + MODCOUNT_OFFSET);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  private void setModCount(int id, int value) throws IOException {
    getRecords().putInt(id * RECORD_SIZE + MODCOUNT_OFFSET, value);
  }

  @Nullable
  public DataInputStream readAttribute(int id, String attId) {
    synchronized (lock) {
      try {
        int encodedAttId = DbConnection.getAttributeId(attId);
        int att_page = getRecords().getInt(id * RECORD_SIZE + ATTREF_OFFSET);
        while (att_page != 0) {
          final DataInputStream page = getAttributes().readStream(att_page);
          int attIdOnPage;
          int next;
          try {
            attIdOnPage = page.readInt();
            next = page.readInt();
          }
          catch (IOException e) {
            LOG.error(DbConnection.handleError(e));
            attIdOnPage = 0;
            next = 0;
          }
          if (attIdOnPage == encodedAttId) {
            return page;
          }

          att_page = next;
          page.close();
        }

        return null;
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  private DataOutputStream findPageToWrite(int id, final int encodedAttId, final int headPage) throws IOException {
    final DataOutputStream result;
    final int resultId;
    incModCount(id);

    int att_page = headPage;

    while (att_page != 0) {
      int curPage = att_page;
      final DataInputStream page = getAttributes().readStream(att_page);
      final int attIdOnPage = page.readInt();
      final int next = page.readInt();
      if (attIdOnPage == encodedAttId) {
        page.close();
        result = getAttributes().writeStream(curPage);
        result.writeInt(encodedAttId);
        result.writeInt(next);

        return result;
      }

      att_page = next;
      page.close();
    }

    resultId = getAttributes().createNewRecord();
    result = getAttributes().writeStream(resultId);
    result.writeInt(encodedAttId);
    result.writeInt(headPage);

    getRecords().putInt(id * RECORD_SIZE + ATTREF_OFFSET, resultId);

    return result;
  }

  @NotNull
  public DataOutputStream writeAttribute(final int id, final String attId) {
    synchronized (lock) {
      try {
        DbConnection.markDirty();
        final int encodedAttId = DbConnection.getAttributeId(attId);
        final int headPage = getRecords().getInt(id * RECORD_SIZE + ATTREF_OFFSET);
        return findPageToWrite(id, encodedAttId, headPage);
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }

  public void disposeAndDeleteFiles() {
    dispose();
  }

  public void dispose() {
    synchronized (lock) {
      try {
        DbConnection.closeFiles();
      }
      catch (IOException e) {
        throw DbConnection.handleError(e);
      }
    }
  }
}