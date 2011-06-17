/*
 * Copyright 2007 Sascha Weinreuter
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
package org.intellij.plugins.xsltDebugger.rt.engine;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 19.05.2007
 */
public class BreakpointManagerImpl implements BreakpointManager {
  private final TIntObjectHashMap<Map<String, Breakpoint>> myBreakpoints =
    new TIntObjectHashMap<Map<String, Breakpoint>>();

  public Breakpoint setBreakpoint(File file, int line) {
    return setBreakpoint(file.toURI().toASCIIString(), line);
  }

  public synchronized void removeBreakpoint(String uri, int line) {
    final Map<String, Breakpoint> s = myBreakpoints.get(line);
    if (s != null) {
      s.remove(normalizeUri(uri));
    }
  }

  public synchronized List<Breakpoint> getBreakpoints() {
    final ArrayList<Breakpoint> breakpoints = new ArrayList<Breakpoint>();
    myBreakpoints.forEachEntry(new TIntObjectProcedure<Map<String, Breakpoint>>() {
      public boolean execute(int i, Map<String, Breakpoint> map) {
        breakpoints.addAll(map.values());
        return true;
      }
    });
    return breakpoints;
  }

  public void removeBreakpoint(Breakpoint bp) {
    removeBreakpoint(bp.getUri(), bp.getLine());
  }

  public synchronized Breakpoint setBreakpoint(String uri, int line) {
    assert line > 0 : "No line number for breakpoint in file " + uri;

    uri = normalizeUri(uri);
    final Map<String, Breakpoint> s = myBreakpoints.get(line);
    final BreakpointImpl bp = new BreakpointImpl(uri, line);
    if (s == null) {
      final HashMap<String, Breakpoint> map = new HashMap<String, Breakpoint>();
      map.put(uri, bp);
      myBreakpoints.put(line, map);
    } else {
      s.put(uri, bp);
    }
    return bp;
  }

  private static String normalizeUri(String uri) {
    // hmm, this code sucks, but seems to be a good guess to ensure the same format of
    // strings (file:/C:/... vs, file:///C:/...) on both sides...
    try {
      try {
        uri = uri.replaceAll(" ", "%20");
        return new File(new URI(uri)).toURI().toASCIIString();
      } catch (IllegalArgumentException e) {
        return new URI(uri).normalize().toASCIIString();
      }
    } catch (URISyntaxException e) {
      System.err.println("Failed to parse <" + uri + ">: " + e);
      return uri;
    }
  }

  public synchronized boolean isBreakpoint(String uri, int lineNumber) {
    final Breakpoint breakpoint = getBreakpoint(uri, lineNumber);
    return breakpoint != null && breakpoint.isEnabled();
  }

  public synchronized Breakpoint getBreakpoint(String uri, int lineNumber) {
    final Map<String, Breakpoint> s = myBreakpoints.get(lineNumber);
    if (s != null) {
      return s.get(normalizeUri(uri));
    }
    return null;
  }
}
