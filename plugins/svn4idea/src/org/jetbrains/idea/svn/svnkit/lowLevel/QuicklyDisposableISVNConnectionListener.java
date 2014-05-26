/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.tmatesoft.svn.core.io.ISVNConnectionListener;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/15/12
 * Time: 3:05 PM
 */
public class QuicklyDisposableISVNConnectionListener extends QuicklyDisposableProxy<ISVNConnectionListener> implements ISVNConnectionListener {
  public QuicklyDisposableISVNConnectionListener(ISVNConnectionListener o) {
    super(o);
  }

  @Override
  public void connectionOpened(SVNRepository repository) {
    getRef().connectionOpened(repository);
  }

  @Override
  public void connectionClosed(SVNRepository repository) {
    getRef().connectionClosed(repository);
  }
}
