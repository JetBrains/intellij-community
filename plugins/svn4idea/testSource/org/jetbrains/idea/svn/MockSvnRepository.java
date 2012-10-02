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
package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.io.ISVNConnectionListener;
import org.tmatesoft.svn.core.io.ISVNSession;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/2/12
 * Time: 7:30 PM
 */
public class MockSvnRepository extends FSRepository {
  private long myCreationTime;
  private boolean mySessionWasClosed;
  private boolean myHaveConnectionListener;

  public MockSvnRepository(SVNURL location, ISVNSession options) {
    super(location, options);
    myCreationTime = System.currentTimeMillis();
    mySessionWasClosed = false;
    myHaveConnectionListener = false;
  }

  @Override
  public void addConnectionListener(ISVNConnectionListener listener) {
    super.addConnectionListener(listener);
    myHaveConnectionListener = true;
  }

  // todo count?
  @Override
  public void removeConnectionListener(ISVNConnectionListener listener) {
    super.removeConnectionListener(listener);
    myHaveConnectionListener = false;
  }

  @Override
  public void closeSession() {
    super.closeSession();
    mySessionWasClosed = true;
  }

  public boolean isSessionWasClosed() {
    return mySessionWasClosed;
  }

  public long getCreationTime() {
    return myCreationTime;
  }

  public boolean isHaveConnectionListener() {
    return myHaveConnectionListener;
  }

  @Override
  public void fireConnectionClosed() {
    super.fireConnectionClosed();
  }

  @Override
  public void fireConnectionOpened() {
    super.fireConnectionOpened();
  }
}
