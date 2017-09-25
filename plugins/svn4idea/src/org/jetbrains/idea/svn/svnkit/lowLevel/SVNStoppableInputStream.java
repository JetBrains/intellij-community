/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.svnkit.lowLevel;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/30/12
 * Time: 6:23 PM
 *
 * SVNLogInputStream is not used, since it does not check available()
 *
 */
public class SVNStoppableInputStream extends InputStream {
  private final static Logger LOG = Logger.getInstance(SVNStoppableInputStream.class);
  private final static String ourCheckAvalilable = "svn.check.available";
  private final InputStream myOriginalIs;
  private final InputStream myIn;
  private boolean myAvailableChecked;
  private final boolean myCheckAvailable;

  public SVNStoppableInputStream(InputStream original, InputStream in) {
    final String property = System.getProperty(ourCheckAvalilable);
    myCheckAvailable = ! StringUtil.isEmptyOrSpaces(property) && Boolean.parseBoolean(property);
    //myCheckAvailable = Boolean.parseBoolean(property);
    myOriginalIs = myCheckAvailable ? digOriginal(original) : original;
    myIn = in;
    myAvailableChecked = false;
  }

  private InputStream digOriginal(InputStream original) {
    // because of many delegates in the chain possible
    InputStream current = original;

    try {
      while (true) {
        final String name = current.getClass().getName();
        if ("org.tmatesoft.svn.core.internal.io.dav.http.SpoolFile.SpoolInputStream".equals(name)) {
          current = byName(current, "myCurrentInput");
        } else if ("org.tmatesoft.svn.core.internal.util.ChunkedInputStream".equals(name)) {
          current = byName(current, "myInputStream");
        } else if ("org.tmatesoft.svn.core.internal.util.FixedSizeInputStream".equals(name)) {
          current = byName(current, "mySource");
        } else if (current instanceof BufferedInputStream) {
          return createReadingProxy(current);
        } else {
          // maybe ok class, maybe some unknown proxy
          Method[] methods = current.getClass().getDeclaredMethods();
          for (Method method : methods) {
            if ("available".equals(method.getName())) {
              return current;
            }
          }
          return createReadingProxy(current);
        }
      }
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      LOG.info(e);
      return createReadingProxy(current);
    }
  }

  private InputStream createReadingProxy(final InputStream current) {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        return current.read();
      }

      public int read(byte[] b) throws IOException {
        return current.read(b);
      }

      public int read(byte[] b, int off, int len) throws IOException {
        return current.read(b, off, len);
      }

      public long skip(long n) throws IOException {
        return current.skip(n);
      }

      public void close() throws IOException {
        current.close();
      }

      public void mark(int readlimit) {
        current.mark(readlimit);
      }

      public void reset() throws IOException {
        current.reset();
      }

      public boolean markSupported() {
        return current.markSupported();
      }

      @Override
      public int available() {
        return 1;
      }
    };
  }

  private InputStream byName(InputStream current, final String name) throws NoSuchFieldException, IllegalAccessException {
    final Field input = current.getClass().getDeclaredField(name);
    input.setAccessible(true);
    current = (InputStream) input.get(current);
    return current;
  }

  @Override
  public int read() throws IOException {
    waitForAvailable();
    return myIn.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    waitForAvailable();
    return myIn.read(b, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    if (n <= 0) return 0;
    check();
    if (available() <= 0) return 0;
    return myIn.skip(n);
  }

  @Override
  public int available() throws IOException {
    check();
    if (! myAvailableChecked) {
      int available = myOriginalIs.available();
      if (available > 0) {
        myAvailableChecked = true;
      }
      return available;
    }
    return 1;
  }

  @Override
  public void close() throws IOException {
    check();
    myIn.close();
  }

  @Override
  public synchronized void mark(int readlimit) {
    myIn.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    check();
    myIn.reset();
  }

  @Override
  public boolean markSupported() {
    return myIn.markSupported();
  }

  private void check() throws IOException {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null && indicator.isCanceled()) {
      throw new IOException("Read request to canceled by user");
    }
  }

  private void waitForAvailable() throws IOException {
    if (! myCheckAvailable) return;
    final Object lock = new Object();
    synchronized (lock) {
      while (available() <= 0) {
        check();
        try {
          lock.wait(100);
        }
        catch (InterruptedException e) {
          //
        }
      }
    }
  }
}
