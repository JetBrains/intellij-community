/*
 * @author max
 */
package com.intellij.util.io.zip;


import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Reimplementation of {@link java.util.zip.ZipOutputStream
 * java.util.zip.ZipOutputStream} that does handle the extended
 * functionality of this package, especially internal/external file
 * attributes and extra fields with different layouts for local file
 * data and central directory entries.
 * <p/>
 * <p>This class will try to use {@link java.io.RandomAccessFile
 * RandomAccessFile} when you know that the output is going to go to a
 * file.</p>
 * <p/>
 * <p>If RandomAccessFile cannot be used, this implementation will use
 * a Data Descriptor to store size and CRC information for {@link
 * #DEFLATED DEFLATED} entries, this means, you don't need to
 * calculate them yourself.  Unfortunately this is not possible for
 * the {@link #STORED STORED} method, here setting the CRC and
 * uncompressed size information is required before {@link
 * #putNextEntry putNextEntry} can be called.</p>
 */
class JBZipOutputStream extends OutputStream {

  private static final int BYTE_MASK = 0xFF;
  private static final int SHORT = 2;
  private static final int WORD = 4;
  private static final int BUFFER_SIZE = 512;

  /**
   * Compression method for deflated entries.
   *
   * @since 1.1
   */
  public static final int DEFLATED = ZipEntry.DEFLATED;

  /**
   * Default compression level for deflated entries.
   *
   * @since Ant 1.7
   */
  public static final int DEFAULT_COMPRESSION = Deflater.DEFAULT_COMPRESSION;

  /**
   * Compression method for stored entries.
   *
   * @since 1.1
   */
  public static final int STORED = ZipEntry.STORED;

  /**
   * Current entry.
   *
   * @since 1.1
   */
  private JBZipEntry entry;

  /**
   * The file comment.
   *
   * @since 1.1
   */
  private String comment = "";

  /**
   * Compression level for next entry.
   *
   * @since 1.1
   */
  private int level = DEFAULT_COMPRESSION;

  /**
   * Has the compression level changed when compared to the last
   * entry?
   *
   * @since 1.5
   */
  private boolean hasCompressionLevelChanged = false;

  /**
   * Default compression method for next entry.
   *
   * @since 1.1
   */
  private int method = ZipEntry.STORED;

  /**
   * CRC instance to avoid parsing DEFLATED data twice.
   *
   * @since 1.1
   */
  private CRC32 crc = new CRC32();

  /**
   * Count the bytes written to out.
   *
   * @since 1.1
   */
  long written = 0;

  /**
   * Data for local header data
   *
   * @since 1.1
   */
  private long dataStart = 0;

  /**
   * Offset for CRC entry in the local file header data for the
   * current entry starts here.
   *
   * @since 1.15
   */
  private long localDataStart = 0;

  /**
   * Start of central directory.
   *
   * @since 1.1
   */
  private long cdOffset = 0;

  /**
   * Length of central directory.
   *
   * @since 1.1
   */
  private long cdLength = 0;

  /**
   * Helper, a 0 as ZipShort.
   *
   * @since 1.1
   */
  private static final byte[] ZERO = {0, 0};

  /**
   * Helper, a 0 as ZipLong.
   *
   * @since 1.1
   */
  private static final byte[] LZERO = {0, 0, 0, 0};


  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   *
   * @since 1.3
   */
  private String encoding = null;

  // CheckStyle:VisibilityModifier OFF - bc

  /**
   * This Deflater object is used for output.
   * <p/>
   * <p>This attribute is only protected to provide a level of API
   * backwards compatibility.  This class used to extend {@link
   * java.util.zip.DeflaterOutputStream DeflaterOutputStream} up to
   * Revision 1.13.</p>
   *
   * @since 1.14
   */
  protected Deflater def = new Deflater(level, true);

  /**
   * This buffer servers as a Deflater.
   * <p/>
   * <p>This attribute is only protected to provide a level of API
   * backwards compatibility.  This class used to extend {@link
   * java.util.zip.DeflaterOutputStream DeflaterOutputStream} up to
   * Revision 1.13.</p>
   *
   * @since 1.14
   */
  protected byte[] buf = new byte[BUFFER_SIZE];

  // CheckStyle:VisibilityModifier ON

  /**
   * Optional random access output.
   *
   * @since 1.14
   */
  private final RandomAccessFile raf;
  private final JBZipFile myFile;

  /**
   * Creates a new ZIP OutputStream writing to a File.  Will use
   * random access if possible.
   *
   * @param file the file to zip to
   * @param currentCDOffset
   * @throws IOException on error
   * @since 1.14
   */
  public JBZipOutputStream(JBZipFile file, long currentCDOffset) {
    myFile = file;
    raf = myFile.archive;
    written = currentCDOffset;
  }

  /**
   * The encoding to use for filenames and the file comment.
   * <p/>
   * <p>For a list of possible values see <a
   * href="http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html">http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html</a>.
   * Defaults to the platform's default character encoding.</p>
   *
   * @param encoding the encoding value
   * @since 1.3
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * The encoding to use for filenames and the file comment.
   *
   * @return null if using the platform's default character encoding.
   * @since 1.3
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * Finishs writing the contents and closes this as well as the
   * underlying stream.
   *
   * @throws IOException on error
   * @since 1.1
   */
  public void finish() throws IOException {
    closeEntry();
    cdOffset = written;
    final List<JBZipEntry> entries = myFile.getEntries();
    for (int i = 0, entriesSize = entries.size(); i < entriesSize; i++) {
      writeCentralFileHeader(entries.get(i));
    }
    cdLength = written - cdOffset;
    writeCentralDirectoryEnd();
  }

  /**
   * Writes all necessary data for this entry.
   *
   * @throws IOException on error
   * @since 1.1
   */
  public void closeEntry() throws IOException {
    if (entry == null) {
      return;
    }

    long realCrc = crc.getValue();
    crc.reset();

    if (entry.getMethod() == ZipEntry.DEFLATED) {
      def.finish();
      while (!def.finished()) {
        deflate();
      }

      entry.setSize(adjustToLong(def.getTotalIn()));
      entry.setCompressedSize(adjustToLong(def.getTotalOut()));
      entry.setCrc(realCrc);

      def.reset();

      written += entry.getCompressedSize();
    }
    else { /* method is STORED and we used RandomAccessFile */
      long size = written - dataStart;

      entry.setSize(size);
      entry.setCompressedSize(size);
      entry.setCrc(realCrc);
    }

    // If random access output, write the local file header containing
    // the correct CRC and compressed/uncompressed sizes
    long save = raf.getFilePointer();

    raf.seek(localDataStart);
    writeOut(ZipLong.getBytes(entry.getCrc()));
    writeOut(ZipLong.getBytes(entry.getCompressedSize()));
    writeOut(ZipLong.getBytes(entry.getSize()));
    raf.seek(save);

    entry = null;
  }

  /**
   * Begin writing next entry.
   *
   * @param ze the entry to write
   * @throws IOException on error
   * @since 1.1
   */
  public void putNextEntry(JBZipEntry ze) throws IOException {
    closeEntry();

    entry = ze;

    if (entry.getMethod() == -1) { // not specified
      entry.setMethod(method);
    }

    if (entry.getTime() == -1) { // not specified
      entry.setTime(System.currentTimeMillis());
    }

    if (entry.getMethod() == ZipEntry.DEFLATED && hasCompressionLevelChanged) {
      def.setLevel(level);
      hasCompressionLevelChanged = false;
    }
    writeLocalFileHeader(entry);
  }

  /**
   * Set the file comment.
   *
   * @param comment the comment
   * @since 1.1
   */
  public void setComment(String comment) {
    this.comment = comment;
  }

  /**
   * Sets the compression level for subsequent entries.
   * <p/>
   * <p>Default is Deflater.DEFAULT_COMPRESSION.</p>
   *
   * @param level the compression level.
   * @throws IllegalArgumentException if an invalid compression level is specified.
   * @since 1.1
   */
  public void setLevel(int level) {
    if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
      throw new IllegalArgumentException("Invalid compression level: " + level);
    }
    hasCompressionLevelChanged = (this.level != level);
    this.level = level;
  }

  /**
   * Sets the default compression method for subsequent entries.
   * <p/>
   * <p>Default is DEFLATED.</p>
   *
   * @param method an <code>int</code> from java.util.zip.ZipEntry
   * @since 1.1
   */
  public void setMethod(int method) {
    this.method = method;
  }

  /**
   * Writes bytes to ZIP entry.
   *
   * @param b      the byte array to write
   * @param offset the start position to write from
   * @param length the number of bytes to write
   * @throws IOException on error
   */
  public void write(byte[] b, int offset, int length) throws IOException {
    if (entry.getMethod() == ZipEntry.DEFLATED) {
      if (length > 0) {
        if (!def.finished()) {
          def.setInput(b, offset, length);
          while (!def.needsInput()) {
            deflate();
          }
        }
      }
    }
    else {
      writeOut(b, offset, length);
      written += length;
    }
    crc.update(b, offset, length);
  }

  /**
   * Writes a single byte to ZIP entry.
   * <p/>
   * <p>Delegates to the three arg method.</p>
   *
   * @param b the byte to write
   * @throws IOException on error
   * @since 1.14
   */
  public void write(int b) throws IOException {
    byte[] buff = new byte[1];
    buff[0] = (byte)(b & BYTE_MASK);
    write(buff, 0, 1);
  }

  /**
   * Closes this output stream and releases any system resources
   * associated with the stream.
   *
   * @throws IOException if an I/O error occurs.
   * @since 1.14
   */
  public void close() throws IOException {
    finish();
  }

  /*
  * Various ZIP constants
  */
  /**
   * local file header signature
   *
   * @since 1.1
   */
  protected static final byte[] LFH_SIG = ZipLong.getBytes(0X04034B50L);
  /**
   * data descriptor signature
   *
   * @since 1.1
   */
  protected static final byte[] DD_SIG = ZipLong.getBytes(0X08074B50L);
  /**
   * central file header signature
   *
   * @since 1.1
   */
  protected static final byte[] CFH_SIG = ZipLong.getBytes(0X02014B50L);
  /**
   * end of central dir signature
   *
   * @since 1.1
   */
  protected static final byte[] EOCD_SIG = ZipLong.getBytes(0X06054B50L);

  /**
   * Writes next block of compressed data to the output stream.
   *
   * @throws IOException on error
   * @since 1.14
   */
  protected final void deflate() throws IOException {
    int len = def.deflate(buf, 0, buf.length);
    if (len > 0) {
      writeOut(buf, 0, len);
    }
  }

  /**
   * Writes the local file header entry
   *
   * @param ze the entry to write
   * @throws IOException on error
   * @since 1.1
   */
  protected void writeLocalFileHeader(JBZipEntry ze) throws IOException {
    ze.setHeaderOffset(written);
    raf.seek(written);

    writeOut(LFH_SIG);
    written += WORD;

    //store method in local variable to prevent multiple method calls
    final int zipMethod = ze.getMethod();

    // version needed to extract
    // general purpose bit flag
    // CheckStyle:MagicNumber OFF
    writeOut(ZipShort.getBytes(10));
    writeOut(ZERO);
    // CheckStyle:MagicNumber ON
    written += WORD;

    // compression method
    writeOut(ZipShort.getBytes(zipMethod));
    written += SHORT;

    // last mod. time and date
    writeOut(ZipLong.getBytes(DosTime.javaToDosTime(ze.getTime())));
    written += WORD;

    // CRC
    // compressed length
    // uncompressed length
    localDataStart = written;
    writeOut(LZERO);
    writeOut(LZERO);
    writeOut(LZERO);
    // CheckStyle:MagicNumber OFF
    written += 12;
    // CheckStyle:MagicNumber ON

    // file name length
    byte[] name = getBytes(ze.getName());
    writeOut(ZipShort.getBytes(name.length));
    written += SHORT;

    // extra field length
    byte[] extra = ze.getLocalFileDataExtra();
    writeOut(ZipShort.getBytes(extra.length));
    written += SHORT;

    // file name
    writeOut(name);
    written += name.length;

    // extra field
    writeOut(extra);
    written += extra.length;

    dataStart = written;
  }

  /**
   * Writes the central file header entry.
   *
   * @param ze the entry to write
   * @throws IOException on error
   * @since 1.1
   */
  protected void writeCentralFileHeader(JBZipEntry ze) throws IOException {
    raf.seek(written);
    writeOut(CFH_SIG);
    written += WORD;

    // version made by
    // CheckStyle:MagicNumber OFF
    writeOut(ZipShort.getBytes((ze.getPlatform() << 8) | 20));
    written += SHORT;

    // version needed to extract
    // general purpose bit flag
    writeOut(ZipShort.getBytes(10));
    writeOut(ZERO);
    // CheckStyle:MagicNumber ON
    written += WORD;

    // compression method
    writeOut(ZipShort.getBytes(ze.getMethod()));
    written += SHORT;

    // last mod. time and date
    writeOut(ZipLong.getBytes(DosTime.javaToDosTime(ze.getTime())));
    written += WORD;

    // CRC
    // compressed length
    // uncompressed length
    writeOut(ZipLong.getBytes(ze.getCrc()));
    writeOut(ZipLong.getBytes(ze.getCompressedSize()));
    writeOut(ZipLong.getBytes(ze.getSize()));
    // CheckStyle:MagicNumber OFF
    written += 12;
    // CheckStyle:MagicNumber ON

    // file name length
    byte[] name = getBytes(ze.getName());
    writeOut(ZipShort.getBytes(name.length));
    written += SHORT;

    // extra field length
    byte[] extra = ze.getExtra();
    writeOut(ZipShort.getBytes(extra.length));
    written += SHORT;

    // file comment length
    String comm = ze.getComment();
    if (comm == null) {
      comm = "";
    }
    byte[] commentB = getBytes(comm);
    writeOut(ZipShort.getBytes(commentB.length));
    written += SHORT;

    // disk number start
    writeOut(ZERO);
    written += SHORT;

    // internal file attributes
    writeOut(ZipShort.getBytes(ze.getInternalAttributes()));
    written += SHORT;

    // external file attributes
    writeOut(ZipLong.getBytes(ze.getExternalAttributes()));
    written += WORD;

    // relative offset of LFH
    writeOut(ZipLong.getBytes(ze.getHeaderOffset()));
    written += WORD;

    // file name
    writeOut(name);
    written += name.length;

    // extra field
    writeOut(extra);
    written += extra.length;

    // file comment
    writeOut(commentB);
    written += commentB.length;
  }

  /**
   * Writes the &quot;End of central dir record&quot;.
   *
   * @throws IOException on error
   * @since 1.1
   */
  protected void writeCentralDirectoryEnd() throws IOException {
    raf.seek(written);
    writeOut(EOCD_SIG);
    written += EOCD_SIG.length;

    // disk numbers
    writeOut(ZERO);
    writeOut(ZERO);
    written += ZERO.length + ZERO.length;

    // number of entries
    byte[] num = ZipShort.getBytes(myFile.getEntries().size());
    writeOut(num);
    writeOut(num);

    written += 2 * num.length;

    // length and location of CD
    writeOut(ZipLong.getBytes(cdLength));
    writeOut(ZipLong.getBytes(cdOffset));

    written += 2 * WORD;

    // ZIP file comment
    byte[] data = getBytes(comment);
    writeOut(ZipShort.getBytes(data.length));
    writeOut(data);

    written += SHORT + data.length;
  }

  /**
   * Smallest date/time ZIP can handle.
   *
   * @since 1.1
   */
  private static final byte[] DOS_TIME_MIN = ZipLong.getBytes(0x00002100L);

  /**
   * Retrieve the bytes for the given String in the encoding set for
   * this Stream.
   *
   * @param name the string to get bytes from
   * @return the bytes as a byte array
   * @throws ZipException on error
   * @since 1.3
   */
  protected byte[] getBytes(String name) throws ZipException {
    if (encoding == null) {
      return name.getBytes();
    }
    else {
      try {
        return name.getBytes(encoding);
      }
      catch (UnsupportedEncodingException uee) {
        throw new ZipException(uee.getMessage());
      }
    }
  }

  /**
   * Write bytes to output or random access file.
   *
   * @param data the byte array to write
   * @throws IOException on error
   * @since 1.14
   */
  protected final void writeOut(byte[] data) throws IOException {
    writeOut(data, 0, data.length);
  }

  /**
   * Write bytes to output or random access file.
   *
   * @param data   the byte array to write
   * @param offset the start position to write from
   * @param length the number of bytes to write
   * @throws IOException on error
   * @since 1.14
   */
  protected final void writeOut(byte[] data, int offset, int length) throws IOException {
    raf.write(data, offset, length);
  }

  /**
   * Assumes a negative integer really is a positive integer that
   * has wrapped around and re-creates the original value.
   *
   * @param i the value to treat as unsigned int.
   * @return the unsigned int as a long.
   * @since 1.34
   */
  protected static long adjustToLong(int i) {
    if (i < 0) {
      return 2 * ((long)Integer.MAX_VALUE) + 2 + i;
    }
    else {
      return i;
    }
  }

}
