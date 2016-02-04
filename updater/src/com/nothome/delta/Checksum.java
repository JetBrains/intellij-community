/*
 *
 * Copyright (c) 2001 Torgeir Veimo
 * Copyright (c) 2002 Nicolas PERIDONT
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
 */

package com.nothome.delta;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Checksum computation class.
 */
public class Checksum {

  private Map<Long, Integer> checksums = new HashMap<Long, Integer>();

  private static final char[] single_hash = {
        /* Random numbers generated using SLIB's pseudo-random number generator. */
    0xbcd1, 0xbb65, 0x42c2, 0xdffe, 0x9666, 0x431b, 0x8504, 0xeb46,
    0x6379, 0xd460, 0xcf14, 0x53cf, 0xdb51, 0xdb08, 0x12c8, 0xf602,
    0xe766, 0x2394, 0x250d, 0xdcbb, 0xa678, 0x02af, 0xa5c6, 0x7ea6,
    0xb645, 0xcb4d, 0xc44b, 0xe5dc, 0x9fe6, 0x5b5c, 0x35f5, 0x701a,
    0x220f, 0x6c38, 0x1a56, 0x4ca3, 0xffc6, 0xb152, 0x8d61, 0x7a58,
    0x9025, 0x8b3d, 0xbf0f, 0x95a3, 0xe5f4, 0xc127, 0x3bed, 0x320b,
    0xb7f3, 0x6054, 0x333c, 0xd383, 0x8154, 0x5242, 0x4e0d, 0x0a94,
    0x7028, 0x8689, 0x3a22, 0x0980, 0x1847, 0xb0f1, 0x9b5c, 0x4176,
    0xb858, 0xd542, 0x1f6c, 0x2497, 0x6a5a, 0x9fa9, 0x8c5a, 0x7743,
    0xa8a9, 0x9a02, 0x4918, 0x438c, 0xc388, 0x9e2b, 0x4cad, 0x01b6,
    0xab19, 0xf777, 0x365f, 0x1eb2, 0x091e, 0x7bf8, 0x7a8e, 0x5227,
    0xeab1, 0x2074, 0x4523, 0xe781, 0x01a3, 0x163d, 0x3b2e, 0x287d,
    0x5e7f, 0xa063, 0xb134, 0x8fae, 0x5e8e, 0xb7b7, 0x4548, 0x1f5a,
    0xfa56, 0x7a24, 0x900f, 0x42dc, 0xcc69, 0x02a0, 0x0b22, 0xdb31,
    0x71fe, 0x0c7d, 0x1732, 0x1159, 0xcb09, 0xe1d2, 0x1351, 0x52e9,
    0xf536, 0x5a4f, 0xc316, 0x6bf9, 0x8994, 0xb774, 0x5f3e, 0xf6d6,
    0x3a61, 0xf82c, 0xcc22, 0x9d06, 0x299c, 0x09e5, 0x1eec, 0x514f,
    0x8d53, 0xa650, 0x5c6e, 0xc577, 0x7958, 0x71ac, 0x8916, 0x9b4f,
    0x2c09, 0x5211, 0xf6d8, 0xcaaa, 0xf7ef, 0x287f, 0x7a94, 0xab49,
    0xfa2c, 0x7222, 0xe457, 0xd71a, 0x00c3, 0x1a76, 0xe98c, 0xc037,
    0x8208, 0x5c2d, 0xdfda, 0xe5f5, 0x0b45, 0x15ce, 0x8a7e, 0xfcad,
    0xaa2d, 0x4b5c, 0xd42e, 0xb251, 0x907e, 0x9a47, 0xc9a6, 0xd93f,
    0x085e, 0x35ce, 0xa153, 0x7e7b, 0x9f0b, 0x25aa, 0x5d9f, 0xc04d,
    0x8a0e, 0x2875, 0x4a1c, 0x295f, 0x1393, 0xf760, 0x9178, 0x0f5b,
    0xfa7d, 0x83b4, 0x2082, 0x721d, 0x6462, 0x0368, 0x67e2, 0x8624,
    0x194d, 0x22f6, 0x78fb, 0x6791, 0xb238, 0xb332, 0x7276, 0xf272,
    0x47ec, 0x4504, 0xa961, 0x9fc8, 0x3fdc, 0xb413, 0x007a, 0x0806,
    0x7458, 0x95c6, 0xccaa, 0x18d6, 0xe2ae, 0x1b06, 0xf3f6, 0x5050,
    0xc8e8, 0xf4ac, 0xc04c, 0xf41c, 0x992f, 0xae44, 0x5f1b, 0x1113,
    0x1738, 0xd9a8, 0x19ea, 0x2d33, 0x9698, 0x2fe9, 0x323f, 0xcde2,
    0x6d71, 0xe37d, 0xb697, 0x2c4f, 0x4373, 0x9102, 0x075d, 0x8e25,
    0x1672, 0xec28, 0x6acb, 0x86cc, 0x186e, 0x9414, 0xd674, 0xd1a5
  };

  /**
   * Initialize checksums for source. The checksum for the <code>chunkSize</code> bytes at offset
   * <code>chunkSize</code> * i is inserted into a hash map.
   */
  public Checksum(SeekableSource source, int chunkSize) throws IOException {
    ByteBuffer bb = ByteBuffer.allocate(chunkSize * 2);
    int count = 0;
    while (true) {
      source.read(bb);
      bb.flip();
      if (bb.remaining() < chunkSize) {
        break;
      }
      while (bb.remaining() >= chunkSize) {
        long queryChecksum = queryChecksum0(bb, chunkSize);
        checksums.put(queryChecksum, count++);
      }
      bb.compact();
    }
  }

  /**
   * Finds the checksum computed from the buffer.
   * Marks, gets, then resets the buffer.
   */
  public static long queryChecksum(ByteBuffer bb, int len) {
    bb.mark();
    long sum = queryChecksum0(bb, len);
    bb.reset();
    return sum;
  }

  private static long queryChecksum0(ByteBuffer bb, int len) {
    int high = 0;
    int low = 0;
    for (int i = 0; i < len; i++) {
      low += single_hash[bb.get() + 128];
      high += low;
    }
    return ((high & 0xffff) << 16) | (low & 0xffff);
  }

  /**
   * Increments a checksum.
   *
   * @param checksum  initial checksum
   * @param out       byte leaving view
   * @param in        byte entering view
   * @param chunkSize size of chunks
   * @return new checksum
   */
  public static long incrementChecksum(long checksum, byte out, byte in, int chunkSize) {
    char old_c = single_hash[out + 128];
    char new_c = single_hash[in + 128];
    int low = ((int)((checksum) & 0xffff) - old_c + new_c) & 0xffff;
    int high = ((int)((checksum) >> 16) - (old_c * chunkSize) + low) & 0xffff;
    return (high << 16) | (low & 0xffff);
  }

  /**
   * Finds the index of a checksum.
   */
  public int findChecksumIndex(long hashf) {
    if (!checksums.containsKey(hashf)) {
      return -1;
    }
    return checksums.get(hashf);
  }

  /**
   * Returns a debug <code>String</code>.
   */
  @Override
  public String toString() {
    return super.toString() +
           " checksums=" + this.checksums;
  }


}
