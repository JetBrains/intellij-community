/*
 * @author max
 */
package com.intellij.util.io.zip;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Extension that adds better handling of extra fields and provides
 * access to the internal and external file attributes.
 */
public class JBZipEntry extends ZipEntry implements Cloneable {

  private static final int PLATFORM_UNIX = 3;
  private static final int PLATFORM_FAT = 0;
  private static final int SHORT_MASK = 0xFFFF;
  private static final int SHORT_SHIFT = 16;

  private int internalAttributes = 0;
  private int platform = PLATFORM_FAT;
  private long externalAttributes = 0;
  private List<ZipExtraField> extraFields = null;
  private String name = null;

  private long headerOffset = -1;
  private long dataOffset = -1;
  private final JBZipFile myFile;


  /**
   * Creates a new zip entry with the specified name.
   *
   * @param name the name of the entry
   * @param file
   * @since 1.1
   */
  public JBZipEntry(String name, JBZipFile file) {
    super(name);
    myFile = file;
  }

  /**
   * Creates a new zip entry with fields taken from the specified zip entry.
   *
   * @param entry the entry to get fields from
   * @param file
   * @throws ZipException on error
   * @since 1.1
   */
  public JBZipEntry(ZipEntry entry, JBZipFile file) throws ZipException {
    super(entry);
    myFile = file;
    byte[] extra = entry.getExtra();
    if (extra != null) {
      setExtraFields(ExtraFieldUtils.parse(extra));
    }
    else {
      // initializes extra data to an empty byte array
      setExtra();
    }
  }

  /**
   * Creates a new zip entry with fields taken from the specified zip entry.
   *
   * @param entry the entry to get fields from
   * @param file
   * @throws ZipException on error
   * @since 1.1
   */
  public JBZipEntry(JBZipEntry entry, JBZipFile file) throws ZipException {
    this((ZipEntry)entry, file);
    setInternalAttributes(entry.getInternalAttributes());
    setExternalAttributes(entry.getExternalAttributes());
    setExtraFields(entry.getExtraFields());
  }

  /**
   * @since 1.9
   * @param file
   */
  protected JBZipEntry(JBZipFile file) {
    super("");
    myFile = file;
  }

  /**
   * Overwrite clone.
   *
   * @return a cloned copy of this ZipEntry
   * @since 1.1
   */
  public Object clone() {
    JBZipEntry e = (JBZipEntry)super.clone();

    e.extraFields = extraFields != null ? new ArrayList<ZipExtraField>(extraFields) : null;
    e.setInternalAttributes(getInternalAttributes());
    e.setExternalAttributes(getExternalAttributes());
    e.setExtraFields(getExtraFields());
    return e;
  }

  /**
   * Retrieves the internal file attributes.
   *
   * @return the internal file attributes
   * @since 1.1
   */
  public int getInternalAttributes() {
    return internalAttributes;
  }

  /**
   * Sets the internal file attributes.
   *
   * @param value an <code>int</code> value
   * @since 1.1
   */
  public void setInternalAttributes(int value) {
    internalAttributes = value;
  }

  /**
   * Retrieves the external file attributes.
   *
   * @return the external file attributes
   * @since 1.1
   */
  public long getExternalAttributes() {
    return externalAttributes;
  }

  /**
   * Sets the external file attributes.
   *
   * @param value an <code>long</code> value
   * @since 1.1
   */
  public void setExternalAttributes(long value) {
    externalAttributes = value;
  }

  public long getHeaderOffset() {
    return headerOffset;
  }

  public void setHeaderOffset(long headerOffset) {
    this.headerOffset = headerOffset;
  }

  public long getDataOffset() {
    return dataOffset;
  }

  public void setDataOffset(long dataOffset) {
    this.dataOffset = dataOffset;
  }

  /**
   * Sets Unix permissions in a way that is understood by Info-Zip's
   * unzip command.
   *
   * @param mode an <code>int</code> value
   * @since Ant 1.5.2
   */
  public void setUnixMode(int mode) {
    // CheckStyle:MagicNumberCheck OFF - no point
    setExternalAttributes((mode << 16)
                          // MS-DOS read-only attribute
                          | ((mode & 0200) == 0 ? 1 : 0)
                          // MS-DOS directory flag
                          | (isDirectory() ? 0x10 : 0));
    // CheckStyle:MagicNumberCheck ON
    platform = PLATFORM_UNIX;
  }

  /**
   * Unix permission.
   *
   * @return the unix permissions
   * @since Ant 1.6
   */
  public int getUnixMode() {
    return (int)((getExternalAttributes() >> SHORT_SHIFT) & SHORT_MASK);
  }

  /**
   * Platform specification to put into the &quot;version made
   * by&quot; part of the central file header.
   *
   * @return 0 (MS-DOS FAT) unless {@link #setUnixMode setUnixMode}
   *         has been called, in which case 3 (Unix) will be returned.
   * @since Ant 1.5.2
   */
  public int getPlatform() {
    return platform;
  }

  /**
   * Set the platform (UNIX or FAT).
   *
   * @param platform an <code>int</code> value - 0 is FAT, 3 is UNIX
   * @since 1.9
   */
  protected void setPlatform(int platform) {
    this.platform = platform;
  }

  /**
   * Replaces all currently attached extra fields with the new array.
   *
   * @param fields an array of extra fields
   * @since 1.1
   */
  public void setExtraFields(ZipExtraField[] fields) {
    extraFields = new ArrayList<ZipExtraField>(Arrays.asList(fields));
    setExtra();
  }

  /**
   * Retrieves extra fields.
   *
   * @return an array of the extra fields
   * @since 1.1
   */
  public ZipExtraField[] getExtraFields() {
    if (extraFields == null) {
      return new ZipExtraField[0];
    }

    return extraFields.toArray(new ZipExtraField[extraFields.size()]);
  }

  /**
   * Adds an extra fields - replacing an already present extra field
   * of the same type.
   *
   * @param ze an extra field
   * @since 1.1
   */
  public void addExtraField(ZipExtraField ze) {
    if (extraFields == null) {
      extraFields = new ArrayList<ZipExtraField>();
    }
    ZipShort type = ze.getHeaderId();
    boolean done = false;
    for (int i = 0, fieldsSize = extraFields.size(); !done && i < fieldsSize; i++) {
      if (extraFields.get(i).getHeaderId().equals(type)) {
        extraFields.set(i, ze);
        done = true;
      }
    }
    if (!done) {
      extraFields.add(ze);
    }
    setExtra();
  }

  /**
   * Remove an extra fields.
   *
   * @param type the type of extra field to remove
   * @since 1.1
   */
  public void removeExtraField(ZipShort type) {
    if (extraFields == null) {
      extraFields = new ArrayList<ZipExtraField>();
    }
    boolean done = false;
    for (int i = 0, fieldsSize = extraFields.size(); !done && i < fieldsSize; i++) {
      if (extraFields.get(i).getHeaderId().equals(type)) {
        extraFields.remove(i);
        done = true;
      }
    }
    if (!done) {
      throw new NoSuchElementException();
    }
    setExtra();
  }

  /**
   * Throws an Exception if extra data cannot be parsed into extra fields.
   *
   * @param extra an array of bytes to be parsed into extra fields
   * @throws RuntimeException if the bytes cannot be parsed
   * @throws RuntimeException on error
   * @since 1.1
   */
  public void setExtra(byte[] extra) throws RuntimeException {
    try {
      setExtraFields(ExtraFieldUtils.parse(extra));
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  /**
   * Unfortunately {@link java.util.zip.ZipOutputStream
   * java.util.zip.ZipOutputStream} seems to access the extra data
   * directly, so overriding getExtra doesn't help - we need to
   * modify super's data directly.
   *
   * @since 1.1
   */
  protected void setExtra() {
    super.setExtra(ExtraFieldUtils.mergeLocalFileDataData(getExtraFields()));
  }

  /**
   * Retrieves the extra data for the local file data.
   *
   * @return the extra data for local file
   * @since 1.1
   */
  public byte[] getLocalFileDataExtra() {
    byte[] extra = getExtra();
    return extra != null ? extra : new byte[0];
  }

  /**
   * Retrieves the extra data for the central directory.
   *
   * @return the central directory extra data
   * @since 1.1
   */
  public byte[] getCentralDirectoryExtra() {
    return ExtraFieldUtils.mergeCentralDirectoryData(getExtraFields());
  }

  /**
   * Make this class work in JDK 1.1 like a 1.2 class.
   * <p/>
   * <p>This either stores the size for later usage or invokes
   * setCompressedSize via reflection.</p>
   *
   * @param size the size to use
   * @since 1.2
   * @deprecated since 1.7.
   *             Use setCompressedSize directly.
   */
  public void setComprSize(long size) {
    setCompressedSize(size);
  }

  /**
   * Get the name of the entry.
   *
   * @return the entry name
   * @since 1.9
   */
  public String getName() {
    return name == null ? super.getName() : name;
  }

  /**
   * Is this entry a directory?
   *
   * @return true if the entry is a directory
   * @since 1.10
   */
  public boolean isDirectory() {
    return getName().endsWith("/");
  }

  /**
   * Set the name of the entry.
   *
   * @param name the name to use
   */
  protected void setName(String name) {
    this.name = name;
  }

  /**
   * Get the hashCode of the entry.
   * This uses the name as the hashcode.
   *
   * @return a hashcode.
   * @since Ant 1.7
   */
  public int hashCode() {
    // this method has severe consequences on performance. We cannot rely
    // on the super.hashCode() method since super.getName() always return
    // the empty string in the current implemention (there's no setter)
    // so it is basically draining the performance of a hashmap lookup
    return getName().hashCode();
  }

  public void erase() {
    myFile.eraseEntry(this);
  }

  /**
   * Returns an InputStream for reading the contents of the given entry.
   *
   * @return a stream to read the entry from.
   * @throws java.io.IOException  if unable to create an input stream from the zipenty
   * @throws java.util.zip.ZipException if the zipentry has an unsupported compression method
   */
  public InputStream getInputStream() throws IOException {
    long start = getDataOffset();
    if (start == -1) return null;

    BoundedInputStream bis = new BoundedInputStream(start, getCompressedSize());
    switch (getMethod()) {
      case STORED:
        return bis;
      case DEFLATED:
        bis.addDummy();
        return new InflaterInputStream(bis, new Inflater(true));
      default:
        throw new ZipException("Found unsupported compression method " + getMethod());
    }
  }

  public void setData(byte[] bytes) throws IOException {
    JBZipOutputStream stream = myFile.getOutputStream();
    stream.putNextEntry(this);
    stream.write(bytes);
    stream.closeEntry();
  }

  /**
   * InputStream that delegates requests to the underlying
   * RandomAccessFile, making sure that only bytes from a certain
   * range can be read.
   */
  public class BoundedInputStream extends InputStream {
    private long remaining;
    private long loc;
    private boolean addDummyByte = false;

    BoundedInputStream(long start, long remaining) {
      this.remaining = remaining;
      loc = start;
    }

    public int read() throws IOException {
      if (remaining-- <= 0) {
        if (addDummyByte) {
          addDummyByte = false;
          return 0;
        }
        return -1;
      }

      RandomAccessFile archive = myFile.archive;
      synchronized (archive) {
        archive.seek(loc++);
        return archive.read();
      }
    }

    public int read(byte[] b, int off, int len) throws IOException {
      if (remaining <= 0) {
        if (addDummyByte) {
          addDummyByte = false;
          b[off] = 0;
          return 1;
        }
        return -1;
      }

      if (len <= 0) {
        return 0;
      }

      if (len > remaining) {
        len = (int)remaining;
      }

      final int ret;
      RandomAccessFile archive = myFile.archive;
      synchronized (archive) {
        archive.seek(loc);
        ret = archive.read(b, off, len);
      }

      if (ret > 0) {
        loc += ret;
        remaining -= ret;
      }
      return ret;
    }

    /**
     * Inflater needs an extra dummy byte for nowrap - see
     * Inflater's javadocs.
     */
    void addDummy() {
      addDummyByte = true;
    }
  }
}
