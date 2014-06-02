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

import org.tmatesoft.svn.core.SVNException;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/2/12
 * Time: 12:11 PM
 */
public interface ApplicationLevelNumberConnectionsGuard {
  void waitForTotalNumberOfConnectionsOk() throws SVNException;
  boolean shouldKeepConnectionLocally();

  void connectionCreated();

  void connectionDestroyed(int number);
}
