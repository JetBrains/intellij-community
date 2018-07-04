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
import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.util.zip.GZIPOutputStream;

/**
 * Java Binary Diff utility. Based on
 * bsdiff (v4.2) by Colin Percival
 * (see http://www.daemonology.net/bsdiff/ ) and distributed under BSD license.
 * <p/>
 * <p>
 * Running this on large files will probably require an increae of the default
 * maximum heap size (use java -Xmx200m)
 * </p>
 *
 * @author Joe Desbonnet, jdesbonnet@gmail.com
 */

public class JBDiff {

  private static final String VERSION = "jbdiff-0.1.1";

  private static int min(int x, int y) {
    return x < y ? x : y;
  }

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
          int tmp = I[k + j];
          I[k + j] = I[k + i];
          I[k + i] = tmp;
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

  private static void split(int[] I, int[] V, int initstart, int initlen, int h) {
    Stack<Integer> startStack = new Stack<Integer>();
    Stack<Integer> lenStack = new Stack<Integer>();
    startStack.push(initstart);
    lenStack.push(initlen);
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
          int tmp = I[i];
          I[i] = I[endLessThanIndex + currentEqualIndex];
          I[endLessThanIndex + currentEqualIndex] = tmp;
          currentEqualIndex++;
        }
        else {
          int tmp = I[i];
          I[i] = I[endLessOrEqualIndex + currentGreaterIndex];
          I[endLessOrEqualIndex + currentGreaterIndex] = tmp;
          currentGreaterIndex++;
        }
      }

      while (endLessThanIndex + currentEqualIndex < endLessOrEqualIndex) {
        if (V[I[endLessThanIndex + currentEqualIndex] + h] == pivot) {
          currentEqualIndex++;
        }
        else {
          int tmp = I[endLessThanIndex + currentEqualIndex];
          I[endLessThanIndex + currentEqualIndex] = I[endLessOrEqualIndex + currentGreaterIndex];
          I[endLessOrEqualIndex + currentGreaterIndex] = tmp;
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
   * Fast suffix sporting.
   * Larsson and Sadakane's qsufsort algorithm.
   * See http://www.cs.lth.se/Research/Algorithms/Papers/jesper5.ps
   *
   * @param I
   * @param V
   * @param oldBuf
   */
  private static void qsufsort(int[] I, int[] V, byte[] oldBuf) {

    int oldsize = oldBuf.length;

    int[] buckets = new int[256];
    int i, h, len;

    for (i = 0; i < 256; i++) {
      buckets[i] = 0;
    }

    for (i = 0; i < oldsize; i++) {
      buckets[(int)oldBuf[i] & 0xff]++;
    }

    for (i = 1; i < 256; i++) {
      buckets[i] += buckets[i - 1];
    }

    for (i = 255; i > 0; i--) {
      buckets[i] = buckets[i - 1];
    }

    buckets[0] = 0;

    for (i = 0; i < oldsize; i++) {
      I[++buckets[(int)oldBuf[i] & 0xff]] = i;
    }

    I[0] = oldsize;
    for (i = 0; i < oldsize; i++) {
      V[i] = buckets[(int)oldBuf[i] & 0xff];
    }
    V[oldsize] = 0;

    for (i = 1; i < 256; i++) {
      if (buckets[i] == buckets[i - 1] + 1) {
        I[buckets[i]] = -1;
      }
    }

    I[0] = -1;

    for (h = 1; I[0] != -(oldsize + 1); h += h) {
      len = 0;
      for (i = 0; i < oldsize + 1;) {
        if (I[i] < 0) {
          len -= I[i];
          i -= I[i];
        }
        else {
          //if(len) I[i-len]=-len;
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

    for (i = 0; i < oldsize + 1; i++) {
      I[V[i]] = i;
    }
  }

  /**
   * Count the number of bytes that match in oldBuf (starting at offset oldOffset)
   * and newBuf (starting at offset newOffset).
   *
   * @param oldBuf
   * @param oldOffset
   * @param newBuf
   * @param newOffset
   * @return
   */
  private final static int matchlen(byte[] oldBuf, int oldOffset, byte[] newBuf,
                                    int newOffset) {
    int end = min(oldBuf.length - oldOffset, newBuf.length - newOffset);
    int i;
    for (i = 0; i < end; i++) {
      if (oldBuf[oldOffset + i] != newBuf[newOffset + i]) {
        break;
      }
    }
    return i;
  }

  private final static int search(int[] I, byte[] oldBuf, byte[] newBuf,
                                  int newBufOffset, int start, int end, IntByRef pos) {
    int x, y;

    if (end - start < 2) {
      x = matchlen(oldBuf, I[start], newBuf, newBufOffset);
      y = matchlen(oldBuf, I[end], newBuf, newBufOffset);

      if (x > y) {
        pos.value = I[start];
        return x;
      }
      else {
        pos.value = I[end];
        return y;
      }
    }

    x = start + (end - start) / 2;
    if (Util.memcmp(oldBuf, I[x], newBuf, newBufOffset) < 0) {
      return search(I, oldBuf, newBuf, newBufOffset, x, end, pos);
    }
    else {
      return search(I, oldBuf, newBuf, newBufOffset, start, x, pos);
    }
  }

  public static byte[] bsdiff(InputStream oldFileIn, InputStream newFileIn, OutputStream diffFileOut)
    throws IOException {

    byte[] oldBuf = Utils.readBytes(oldFileIn);
    int oldsize = oldBuf.length;

    int[] I = new int[oldsize + 1];
    int[] V = new int[oldsize + 1];

    qsufsort(I, V, oldBuf);

    //free(V)
    V = null;
    System.gc();

    byte[] newBuf = Utils.readBytes(newFileIn);
    int newsize = newBuf.length;

    // diff block
    int dblen = 0;
    byte[] db = new byte[newsize];

    // extra block
    int eblen = 0;
    byte[] eb = new byte[newsize];

    /*
                 * Diff file is composed as follows:
                 *
                 * Header (32 bytes)
                 * Data (from offset 32 to end of file)
                 *
                 * Header:
                 * Offset 0, length 8 bytes: file magic "jbdiff40"
                 * Offset 8, length 8 bytes: length of ctrl block
                 * Offset 16, length 8 bytes: length of compressed diff block
                 * Offset 24, length 8 bytes: length of new file
                 *
                 * Data:
                 * 32  (length ctrlBlockLen): ctrlBlock
                 * 32+ctrlBlockLen (length diffBlockLen): diffBlock (gziped)
                 * 32+ctrlBlockLen+diffBlockLen (to end of file): extraBlock (gziped)
                 *
                 * ctrlBlock comprises a set of records, each record 12 bytes. A record
                 * comprises 3 x 32 bit integers. The ctrlBlock is not compressed.
                 */

    ByteArrayOutputStream arrayOut = new OpenByteArrayOutputStream();
    DataOutputStream diffOut = new DataOutputStream(arrayOut);

    int oldscore, scsc;

    int overlap, Ss, lens;
    int i;
    int scan = 0;
    int len = 0;
    int lastscan = 0;
    int lastpos = 0;
    int lastoffset = 0;

    IntByRef pos = new IntByRef();
    int ctrlBlockLen = 0;

    while (scan < newsize) {

      oldscore = 0;

      for (scsc = scan += len; scan < newsize; scan++) {

        len = search(I, oldBuf, newBuf, scan, 0, oldsize, pos);

        for (; scsc < scan + len; scsc++) {
          if ((scsc + lastoffset < oldsize)
              && (oldBuf[scsc + lastoffset] == newBuf[scsc])) {
            oldscore++;
          }
        }

        if (((len == oldscore) && (len != 0)) || (len > oldscore + 8)) {
          break;
        }

        if ((scan + lastoffset < oldsize)
            && (oldBuf[scan + lastoffset] == newBuf[scan])) {
          oldscore--;
        }
      }

      if ((len != oldscore) || (scan == newsize)) {
        int s = 0;
        int Sf = 0;
        int lenf = 0;
        for (i = 0; (lastscan + i < scan) && (lastpos + i < oldsize);) {
          if (oldBuf[lastpos + i] == newBuf[lastscan + i]) {
            s++;
          }
          i++;
          if (s * 2 - i > Sf * 2 - lenf) {
            Sf = s;
            lenf = i;
          }
        }

        int lenb = 0;
        if (scan < newsize) {
          s = 0;
          int Sb = 0;
          for (i = 1; (scan >= lastscan + i) && (pos.value >= i); i++) {
            if (oldBuf[pos.value - i] == newBuf[scan - i]) {
              s++;
            }
            if (s * 2 - i > Sb * 2 - lenb) {
              Sb = s;
              lenb = i;
            }
          }
        }

        if (lastscan + lenf > scan - lenb) {
          overlap = (lastscan + lenf) - (scan - lenb);
          s = 0;
          Ss = 0;
          lens = 0;
          for (i = 0; i < overlap; i++) {
            if (newBuf[lastscan + lenf - overlap + i] == oldBuf[lastpos
                                                                + lenf - overlap + i]) {
              s++;
            }
            if (newBuf[scan - lenb + i] == oldBuf[pos.value - lenb + i]) {
              s--;
            }
            if (s > Ss) {
              Ss = s;
              lens = i + 1;
            }
          }

          lenf += lens - overlap;
          lenb -= lens;
        }

        // ? byte casting introduced here -- might affect things
        for (i = 0; i < lenf; i++) {
          db[dblen + i] = (byte)(newBuf[lastscan + i] - oldBuf[lastpos
                                                               + i]);
        }

        for (i = 0; i < (scan - lenb) - (lastscan + lenf); i++) {
          eb[eblen + i] = newBuf[lastscan + lenf + i];
        }

        dblen += lenf;
        eblen += (scan - lenb) - (lastscan + lenf);

        /*
                                  * Write control block entry (3 x int)
                                  */
        diffOut.writeInt(lenf);
        diffOut.writeInt((scan - lenb) - (lastscan + lenf));
        diffOut.writeInt((pos.value - lenb) - (lastpos + lenf));
        ctrlBlockLen += 12;

        lastscan = scan - lenb;
        lastpos = pos.value - lenb;
        lastoffset = pos.value - scan;
      } // end if
    } // end while loop

    GZIPOutputStream gzOut;

    /*
                 * Write diff block
                 */
    gzOut = new GZIPOutputStream(diffOut);
    gzOut.write(db, 0, dblen);
    gzOut.finish();
    int diffBlockLen = diffOut.size() - ctrlBlockLen;

    /*
      * Write extra block
      */
    gzOut = new GZIPOutputStream(diffOut);
    gzOut.write(eb, 0, eblen);
    gzOut.finish();

    diffOut.close();

    DataOutputStream headerStream = new DataOutputStream(diffFileOut);
    headerStream.write("jbdiff40".getBytes(StandardCharsets.US_ASCII));
    headerStream.writeLong(ctrlBlockLen);  // ctrlBlockLen (compressed)
    headerStream.writeLong(diffBlockLen);  // diffBlockLen (compressed)
    headerStream.writeLong(newsize);
    headerStream.flush();

    arrayOut.writeTo(diffFileOut);

    return newBuf;
  }

  private static class IntByRef {
    public int value;
  }
}

