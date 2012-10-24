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

import com.intellij.vcsUtil.LearningProxy;
import org.jetbrains.idea.svn.portable.SVNChangelistClientI;
import org.jetbrains.idea.svn.portable.SvnUpdateClientI;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/19/12
 * Time: 4:22 PM
 */
public class SvnProxies {
  private final SvnAbstractWriteOperationLocks myLocks;
  private final AtomicReference<LearningProxy<SVNChangelistClientI, SVNException>> myChangelist;
  private final AtomicReference<LearningProxy<SvnUpdateClientI, SVNException>> myUpdate;

  public SvnProxies(SvnAbstractWriteOperationLocks locks) {
    myLocks = locks;
    myChangelist = new AtomicReference<LearningProxy<SVNChangelistClientI, SVNException>>();
    myUpdate = new AtomicReference<LearningProxy<SvnUpdateClientI, SVNException>>();
  }

  public LearningProxy<SVNChangelistClientI, SVNException> getChangelistsProfessor(final File file) {
    if (myChangelist.get() == null) {
      final MyLearningProxy<SVNChangelistClientI> proxy = new MyLearningProxy<SVNChangelistClientI>(myLocks, file);
      final SVNChangelistClientI learn = proxy.learn(SVNChangelistClientI.class);
      try {
        learn.addToChangelist(null, null, null, null);
        learn.doAddToChangelist(null, null, null, null);
        learn.doRemoveFromChangelist(null, null, null);
        learn.removeFromChangelist(null, null, null);
      } catch (SVNException e) {
        //can not occur since methods are not really called
        throw new RuntimeException(e);
      }

      myChangelist.set(proxy);
    }
    return myChangelist.get();
  }

  public LearningProxy<SvnUpdateClientI, SVNException> getUpdateProfessor(final File file) {
    if (myUpdate.get() == null) {
      final MyLearningProxy<SvnUpdateClientI> proxy = new MyLearningProxy<SvnUpdateClientI>(myLocks, file);
      final SvnUpdateClientI learn = proxy.learn(SvnUpdateClientI.class);
      try {
        learn.doUpdate(null, null, false);
        learn.doUpdate(null, null, false, false);
        learn.doUpdate((File[]) null, null, null, false, false);
        learn.doUpdate((File[]) null, null, null, false, false, false);
        learn.doUpdate((File) null, null, null, false, false);

        learn.doSwitch(null, null, null, false);
        learn.doSwitch(null, null, null, null, false);
        learn.doSwitch(null, null, null, null, false, false);
        learn.doSwitch(null, null, null, null, null, false, false);
        learn.doSwitch(null, null, null, null, null, false, false, false);

        learn.doCheckout(null, null, null, null, false);
        // todo continue
        //learn.doCheckout();
        //learn.doCheckout();
      }
      catch (SVNException e) {
        //can not occur since methods are not really called
        throw new RuntimeException(e);
      }
      myUpdate.set(proxy);
    }
    return myUpdate.get();
  }

  private static class MyLearningProxy<T> extends LearningProxy<T, SVNException> {
    private final SvnAbstractWriteOperationLocks myLocks;
    private final File myFile;

    private MyLearningProxy(SvnAbstractWriteOperationLocks locks, final File file) {
      myLocks = locks;
      myFile = file;
    }

    @Override
    protected void onBefore() throws SVNException {
      myLocks.lockWrite(myFile);
    }

    @Override
    protected void onAfter() throws SVNException {
      myLocks.unlockWrite(myFile);
    }
  }
}
