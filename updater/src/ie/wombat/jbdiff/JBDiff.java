/*
 * Copyright (c) 2005, Joe Desbonnet, (jdesbonnet@gmail.com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <copyright holder> ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <copyright holder> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ie.wombat.jbdiff;

import com.intellij.updater.Utils;
import com.intellij.updater.Utils.OpenByteArrayOutputStream;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.GZIPOutputStream;

/**
 * Java Binary Diff utility. Based on <a href="http://www.daemonology.net/bsdiff/">bsdiff (v4.2)</a> by Colin Percival.
 * Running this on large files will probably require an increase of the default maximum heap size.
 *
 * @author <a href="maito:jdesbonnet@gmail.com">Joe Desbonnet</a>
 */
public class JBDiff {
  private static void selectSplit(int[] I, int[] V, int start, int len, int h) {
    int j;
    for (int k = start; k < start + len; k += j) {
      j = 1;
      int x = V[I[k] + h];
      for (int i = 1; k + i < start + len; i++) {
        if (V[I[k + i] + h] < x) {
          x = V[I[k + i] + h];
          j = 0;
        }

        if (V[I[k + i] + h] == x) {
          swap(I, k + j, k + i);
          j++;
        }
      }

      for (int i = 0; i < j; i++) {
        V[I[k + i]] = k + j - 1;
      }
      if (j == 1) {
        I[k] = -1;
      }
    }
  }

  private static void split(int[] I, int[] V, int initStart, int initLen, int h) {
    Deque<Integer> startStack = new ArrayDeque<>();
    Deque<Integer> lenStack = new ArrayDeque<>();
    startStack.push(initStart);
    lenStack.push(initLen);
    while (!startStack.isEmpty()) {
      int start = startStack.pop();
      int len = lenStack.pop();

      if (len < 16) {
        selectSplit(I, V, start, len, h);
        continue;
      }

      int pivot = V[I[start + len / 2] + h];
      int endLessThanIndex = 0;
      int endLessOrEqualIndex = 0;
      for (int i = start; i < start + len; i++) {
        if (V[I[i] + h] < pivot) {
          endLessThanIndex++;
        }
        if (V[I[i] + h] == pivot) {
          endLessOrEqualIndex++;
        }
      }

      endLessThanIndex += start;
      endLessOrEqualIndex += endLessThanIndex;

      int i = start;
      int currentEqualIndex = 0;
      int currentGreaterIndex = 0;
      while (i < endLessThanIndex) {
        if (V[I[i] + h] < pivot) {
          i++;
        }
        else if (V[I[i] + h] == pivot) {
          swap(I, i, endLessThanIndex + currentEqualIndex);
          currentEqualIndex++;
        }
        else {
          swap(I, i, endLessOrEqualIndex + currentGreaterIndex);
          currentGreaterIndex++;
        }
      }

      while (endLessThanIndex + currentEqualIndex < endLessOrEqualIndex) {
        if (V[I[endLessThanIndex + currentEqualIndex] + h] == pivot) {
          currentEqualIndex++;
        }
        else {
          swap(I, endLessThanIndex + currentEqualIndex, endLessOrEqualIndex + currentGreaterIndex);
          currentGreaterIndex++;
        }
      }

      for (i = endLessThanIndex; i < endLessOrEqualIndex; i++) {
        V[I[i]] = endLessOrEqualIndex - 1;
      }

      if (endLessThanIndex == endLessOrEqualIndex - 1) {
        I[endLessThanIndex] = -1;
      }

      if (endLessThanIndex > start) {
        startStack.push(start);
        lenStack.push(endLessThanIndex - start);
      }

      if (start + len > endLessOrEqualIndex) {
        startStack.push(endLessOrEqualIndex);
        lenStack.push(start + len - endLessOrEqualIndex);
      }
    }
  }

  /**
   * Fast suffix sporting by <a href="http://www.larsson.dogma.net/ssrev-tr.pdf">Larsson and Sadakane</a>.
   */
  private static void sort(int[] I, byte[] oldBuf) {
    int oldSize = oldBuf.length;
    int[] V = new int[oldSize + 1];

    int[] buckets = new int[256];
    int i, h, len;

    for (i = 0; i < 256; i++) {
      buckets[i] = 0;
    }

    for (i = 0; i < oldSize; i++) {
      buckets[(int)oldBuf[i] & 0xff]++;
    }

    for (i = 1; i < 256; i++) {
      buckets[i] += buckets[i - 1];
    }

    for (i = 255; i > 0; i--) {
      buckets[i] = buckets[i - 1];
    }

    buckets[0] = 0;

    for (i = 0; i < oldSize; i++) {
      I[++buckets[(int)oldBuf[i] & 0xff]] = i;
    }

    I[0] = oldSize;
    for (i = 0; i < oldSize; i++) {
      V[i] = buckets[(int)oldBuf[i] & 0xff];
    }
    V[oldSize] = 0;

    for (i = 1; i < 256; i++) {
      if (buckets[i] == buckets[i - 1] + 1) {
        I[buckets[i]] = -1;
      }
    }

    I[0] = -1;

    for (h = 1; I[0] != -(oldSize + 1); h += h) {
      len = 0;
      for (i = 0; i < oldSize + 1;) {
        if (I[i] < 0) {
          len -= I[i];
          i -= I[i];
        }
        else {
          if (len != 0) {
            I[i - len] = -len;
          }
          len = V[I[i]] + 1 - i;
          split(I, V, i, len, h);
          i += len;
          len = 0;
        }
      }

      if (len != 0) {
        I[i - len] = -len;
      }
    }

    for (i = 0; i < oldSize + 1; i++) {
      I[V[i]] = i;
    }

    //noinspection UnusedAssignment
    V = null;
  }

  /**
   * Count the number of bytes that match in oldBuf (starting at offset oldOffset) and newBuf (starting at offset newOffset).
   */
  private static int matchLen(byte[] oldBuf, int oldOffset, byte[] newBuf, int newOffset) {
    int end = Math.min(oldBuf.length - oldOffset, newBuf.length - newOffset);
    for (int i = 0; i < end; i++) {
      if (oldBuf[oldOffset + i] != newBuf[newOffset + i]) {
        return i;
      }
    }
    return end;
  }

  private static int search(int[] I, byte[] oldBuf, byte[] newBuf, int newBufOffset, int start, int end, IntByRef pos) {
    if (end - start < 2) {
      int x = matchLen(oldBuf, I[start], newBuf, newBufOffset);
      int y = matchLen(oldBuf, I[end], newBuf, newBufOffset);
      if (x > y) {
        pos.value = I[start];
        return x;
      }
      else {
        pos.value = I[end];
        return y;
      }
    }

    int middle = start + (end - start) / 2;
    if (memCmp(oldBuf, I[middle], newBuf, newBufOffset) < 0) {
      return search(I, oldBuf, newBuf, newBufOffset, middle, end, pos);
    }
    else {
      return search(I, oldBuf, newBuf, newBufOffset, start, middle, pos);
    }
  }

  public static byte[] bsdiff(InputStream oldFileIn, InputStream newFileIn, ByteArrayOutputStream diffFileOut, int timeout) throws IOException {
    byte[] oldBuf = Utils.readBytes(oldFileIn);
    int oldSize = oldBuf.length;

    int[] I = new int[oldSize + 1];

    sort(I, oldBuf);

    byte[] newBuf = Utils.readBytes(newFileIn);
    int newSize = newBuf.length;

    // diff block
    int dbLen = 0;
    byte[] db = new byte[newSize];

    // extra block
    int ebLen = 0;
    byte[] eb = new byte[newSize];

    /*
     * Diff file is composed as follows:
     * Header (24 bytes)
     * Data (from offset 24 to end of file)
     *
     * Header:
     * Offset 0, length 8 bytes: length of ctrl block
     * Offset 8, length 8 bytes: length of compressed diff block
     * Offset 16, length 8 bytes: length of new file
     *
     * Data:
     * 24 (length ctrlBlockLen): ctrlBlock
     * 24 + ctrlBlockLen (length diffBlockLen): diffBlock (GZIPed)
     * 24 + ctrlBlockLen + diffBlockLen (to end of file): extraBlock (GZIPed)
     *
     * ctrlBlock comprises a set of records, each record 12 bytes. A record
     * comprises 3 x 32 bit integers. The ctrlBlock is not compressed.
     */

    ByteArrayOutputStream arrayOut = new OpenByteArrayOutputStream();
    DataOutputStream diffOut = new DataOutputStream(arrayOut);

    int oldScore, scSc;
    int overlap, ss, lenS;
    int scan = 0;
    int len = 0;
    int lastScan = 0;
    int lastPos = 0;
    int lastOffset = 0;

    IntByRef pos = new IntByRef();
    int ctrlBlockLen = 0;

    long stop = timeout > 0 ? System.nanoTime() + timeout * 1_000_000_000L : 0;

    while (scan < newSize) {
      if (stop != 0 && System.nanoTime() > stop) {
        diffFileOut.reset();
        return newBuf;
      }

      oldScore = 0;

      for (scSc = scan += len; scan < newSize; scan++) {
        len = search(I, oldBuf, newBuf, scan, 0, oldSize, pos);

        for (; scSc < scan + len; scSc++) {
          if ((scSc + lastOffset < oldSize)
              && (oldBuf[scSc + lastOffset] == newBuf[scSc])) {
            oldScore++;
          }
        }

        if (((len == oldScore) && (len != 0)) || (len > oldScore + 8)) {
          break;
        }

        if ((scan + lastOffset < oldSize)
            && (oldBuf[scan + lastOffset] == newBuf[scan])) {
          oldScore--;
        }
      }

      if ((len != oldScore) || (scan == newSize)) {
        int s = 0;
        int Sf = 0;
        int lenF = 0;
        for (int i = 0; (lastScan + i < scan) && (lastPos + i < oldSize); i++) {
          if (oldBuf[lastPos + i] == newBuf[lastScan + i]) {
            s++;
          }
          if (s * 2 - i + 1 > Sf * 2 - lenF) {
            Sf = s;
            lenF = i + 1;
          }
        }

        int lenB = 0;
        if (scan < newSize) {
          s = 0;
          int Sb = 0;
          for (int i = 1; (scan >= lastScan + i) && (pos.value >= i); i++) {
            if (oldBuf[pos.value - i] == newBuf[scan - i]) {
              s++;
            }
            if (s * 2 - i > Sb * 2 - lenB) {
              Sb = s;
              lenB = i;
            }
          }
        }

        if (lastScan + lenF > scan - lenB) {
          overlap = (lastScan + lenF) - (scan - lenB);
          s = 0;
          ss = 0;
          lenS = 0;
          for (int i = 0; i < overlap; i++) {
            if (newBuf[lastScan + lenF - overlap + i] == oldBuf[lastPos
                                                                + lenF - overlap + i]) {
              s++;
            }
            if (newBuf[scan - lenB + i] == oldBuf[pos.value - lenB + i]) {
              s--;
            }
            if (s > ss) {
              ss = s;
              lenS = i + 1;
            }
          }

          lenF += lenS - overlap;
          lenB -= lenS;
        }

        // ? byte casting introduced here -- might affect things
        for (int i = 0; i < lenF; i++) {
          db[dbLen + i] = (byte)(newBuf[lastScan + i] - oldBuf[lastPos + i]);
        }

        for (int i = 0; i < (scan - lenB) - (lastScan + lenF); i++) {
          eb[ebLen + i] = newBuf[lastScan + lenF + i];
        }

        dbLen += lenF;
        ebLen += (scan - lenB) - (lastScan + lenF);

        /* Write control block entry (3 x int) */
        diffOut.writeInt(lenF);
        diffOut.writeInt((scan - lenB) - (lastScan + lenF));
        diffOut.writeInt((pos.value - lenB) - (lastPos + lenF));
        ctrlBlockLen += 12;

        lastScan = scan - lenB;
        lastPos = pos.value - lenB;
        lastOffset = pos.value - scan;
      }
    }

    //noinspection UnusedAssignment
    I = null;

    /* Write diff block */
    GZIPOutputStream dbOut = new GZIPOutputStream(diffOut);
    dbOut.write(db, 0, dbLen);
    dbOut.finish();
    int diffBlockLen = diffOut.size() - ctrlBlockLen;

    /* Write extra block */
    GZIPOutputStream ebOut = new GZIPOutputStream(diffOut);
    ebOut.write(eb, 0, ebLen);
    ebOut.finish();

    diffOut.close();

    DataOutputStream headerStream = new DataOutputStream(diffFileOut);
    headerStream.writeLong(ctrlBlockLen);  // ctrlBlockLen (compressed)
    headerStream.writeLong(diffBlockLen);  // diffBlockLen (compressed)
    headerStream.writeLong(newSize);
    headerStream.flush();

    arrayOut.writeTo(diffFileOut);

    return newBuf;
  }

  private static class IntByRef {
    public int value;
  }

  private static void swap(int[] array, int i, int j) {
    int tmp = array[i];
    array[i] = array[j];
    array[j] = tmp;
  }

  private static int memCmp(byte[] s1, int s1offset, byte[] s2, int s2offset) {
    int n = s1.length - s1offset;
    if (n > (s2.length - s2offset)) {
      n = s2.length - s2offset;
    }
    for (int i = 0; i < n; i++) {
      if (s1[i + s1offset] != s2[i + s2offset]) {
        return s1[i + s1offset] < s2[i + s2offset] ? -1 : 1;
      }
    }
    return 0;
  }
}
