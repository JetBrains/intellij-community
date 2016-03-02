/*
 *
 * Copyright (c) 2001 Torgeir Veimo
 * Copyright (c) 2002 Nicolas PERIDONT
 * Bug Fixes: Daniel Morrione dan@morrione.net
 * Copyright (c) 2006 Heiko Klein
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 * Change Log:
 * iiimmddyyn  nnnnn  Description
 * ----------  -----  -------------------------------------------------------
 * gls100603a         Fixes from Torgeir Veimo and Dan Morrione
 * gls110603a         Stream not being closed thus preventing a file from
 *                       being subsequently deleted.
 * gls031504a         Error being written to stderr rather than throwing exception
 */

package com.nothome.delta;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Class for computing deltas against a source.
 * The source file is read by blocks and a hash is computed per block.
 * Then the target is scanned for matching blocks.
 * <p/>
 * This class is not thread safe. Use one instance per thread.
 * <p/>
 * This class should support files over 4GB in length, although you must
 * use a larger checksum size, such as 1K, as all checksums use "int" indexing.
 * Newer versions may eventually support paging in/out of checksums.
 */
public class Delta {

  /**
   * Default size of 16.
   * For "Lorem ipsum" text files (see the tests) the ideal size is about 14.
   * Any smaller and the patch size becomes actually be larger.
   * <p>
   * Use a size like 64 or 128 for large files.
   */
  public static final int DEFAULT_CHUNK_SIZE = 1 << 4;

  /**
   * Chunk Size.
   */
  private int S;

  private GDiffWriter output;

  /**
   * Constructs a new Delta.
   * In the future, additional constructor arguments will set the algorithm details.
   */
  public Delta() {
    setChunkSize(DEFAULT_CHUNK_SIZE);
  }

  /**
   * Sets the chunk size used.
   * Larger chunks are faster and use less memory, but create larger patches
   * as well.
   *
   * @param size
   */
  public void setChunkSize(int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("Invalid size");
    }
    S = size;
  }

  /**
   * Compares the source bytes with target bytes, writing to output.
   *
   * @param outputStream will be closed
   */
  public void compute(File sourceFile, File targetFile, OutputStream outputStream)
    throws IOException {
    SourceState source = new SourceState(new RandomAccessFileSeekableSource(new RandomAccessFile(sourceFile, "r")));
    TargetState target = new TargetState(new BufferedInputStream(new FileInputStream(targetFile)));
    GDiffWriter output = new GDiffWriter(outputStream);

    this.output = output;

    while (!target.eof()) {
      int index = target.find(source);
      if (index != -1) {
        long offset = (long)index * S;
        source.seek(offset);
        int match = target.longestMatch(source);
        if (match >= S) {
          output.addCopy(offset, match);
        }
        else {
          // move the position back according to how much we can't copy
          target.tbuf.position(target.tbuf.position() - match);
          addData(target);
        }
      }
      else {
        addData(target);
      }
    }
    output.end();
  }

  private void addData(TargetState target) throws IOException {
    int i = target.read();
    if (i == -1) {
      return;
    }
    output.addData((byte)i);
  }

  class SourceState {

    private Checksum checksum;
    private SeekableSource source;

    public SourceState(SeekableSource source) throws IOException {
      checksum = new Checksum(source, S);
      this.source = source;
      source.seek(0);
    }

    public void seek(long index) throws IOException {
      source.seek(index);
    }

    /**
     * Returns a debug <code>String</code>.
     */
    @Override
    public String toString() {
      return "Source" +
             " checksum=" + this.checksum +
             " source=" + this.source;
    }

  }

  class TargetState {

    private ReadableByteChannel c;
    private ByteBuffer tbuf = ByteBuffer.allocate(blocksize());
    private ByteBuffer sbuf = ByteBuffer.allocate(blocksize());
    private long hash;
    private boolean hashReset = true;
    private boolean eof;

    TargetState(InputStream targetIS) throws IOException {
      c = Channels.newChannel(targetIS);
      tbuf.limit(0);
    }

    private int blocksize() {
      return Math.min(1024 * 16, S * 4);
    }

    /**
     * Returns the index of the next N bytes of the stream.
     */
    public int find(SourceState source) throws IOException {
      if (eof) {
        return -1;
      }
      sbuf.clear();
      sbuf.limit(0);
      if (hashReset) {
        while (tbuf.remaining() < S) {
          tbuf.compact();
          int read = c.read(tbuf);
          tbuf.flip();
          if (read == -1) {
            return -1;
          }
        }
        hash = Checksum.queryChecksum(tbuf, S);
        hashReset = false;
      }
      return source.checksum.findChecksumIndex(hash);
    }

    public boolean eof() {
      return eof;
    }

    /**
     * Reads a byte.
     *
     * @throws IOException
     */
    public int read() throws IOException {
      if (tbuf.remaining() <= S) {
        readMore();
        if (!tbuf.hasRemaining()) {
          eof = true;
          return -1;
        }
      }
      byte b = tbuf.get();
      if (tbuf.remaining() >= S) {
        byte nchar = tbuf.get(tbuf.position() + S - 1);
        hash = Checksum.incrementChecksum(hash, b, nchar, S);
      }
      return b & 0xFF;
    }

    /**
     * Returns the longest match length at the source location.
     */
    public int longestMatch(SourceState source) throws IOException {
      int match = 0;
      hashReset = true;
      while (true) {
        if (!sbuf.hasRemaining()) {
          sbuf.clear();
          int read = source.source.read(sbuf);
          sbuf.flip();
          if (read == -1) {
            return match;
          }
        }
        if (!tbuf.hasRemaining()) {
          readMore();
          if (!tbuf.hasRemaining()) {
            eof = true;
            return match;
          }
        }
        if (sbuf.get() != tbuf.get()) {
          tbuf.position(tbuf.position() - 1);
          return match;
        }
        match++;
      }
    }

    private void readMore() throws IOException {
      tbuf.compact();
      c.read(tbuf);
      tbuf.flip();
    }

    /**
     * Returns a debug <code>String</code>.
     */
    @Override
    public String toString() {
      return "Target[" +
             " targetBuff=" + dump() + // this.tbuf +
             " sourceBuff=" + this.sbuf +
             " hashf=" + this.hash +
             " eof=" + this.eof +
             "]";
    }

    private String dump() {
      return dump(tbuf);
    }

    private String dump(ByteBuffer bb) {
      return getTextDump(bb);
    }

    private void append(StringBuffer sb, int value) {
      char b1 = (char)((value >> 4) & 0x0F);
      char b2 = (char)((value) & 0x0F);
      sb.append(Character.forDigit(b1, 16));
      sb.append(Character.forDigit(b2, 16));
    }

    public String getTextDump(ByteBuffer bb) {
      StringBuffer sb = new StringBuffer(bb.remaining() * 2);
      bb.mark();
      while (bb.hasRemaining()) {
        int val = bb.get();
        if (val > 32 && val < 127) {
          sb.append(" ").append((char)val);
        }
        else {
          append(sb, val);
        }
      }
      bb.reset();
      return sb.toString();
    }

  }
}
