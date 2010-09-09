/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.Assert;
import org.jetbrains.idea.svn.auth.ProviderType;
import org.jetbrains.idea.svn.auth.SvnAuthenticationInteraction;
import org.jetbrains.idea.svn.auth.SvnAuthenticationListener;
import org.jetbrains.idea.svn.dialogs.SvnAuthenticationProvider;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SvnAuthenticationTest extends PlatformTestCase {
  private SvnAuthenticationManager myAuthenticationManager;
  private TestInteraction myTestInteraction;
  private SvnVcs myVcs;
  private final Object mySynchObject = new Object();
  private SvnTestInteractiveAuthentication myInteractiveProvider;
  private SvnConfiguration myConfiguration;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myConfiguration = SvnConfiguration.getInstance(myProject);
    final String configPath = myProject.getBaseDir().getPath() + File.separator + "Subversion";
    myConfiguration.setConfigurationDirectory(configPath);

    final File configFile = new File(configPath);
    myFilesToDelete.add(configFile);

    myVcs = SvnVcs.getInstance(myProject);

    myAuthenticationManager = new SvnAuthenticationManager(myProject, configFile);

    myInteractiveProvider = new SvnTestInteractiveAuthentication(myAuthenticationManager);
    myAuthenticationManager.setAuthenticationProvider(new SvnAuthenticationProvider(myVcs, myInteractiveProvider));
    myAuthenticationManager.setRuntimeStorage(SvnConfiguration.RUNTIME_AUTH_CACHE);

    myTestInteraction = new TestInteraction();
    myAuthenticationManager.setInteraction(myTestInteraction);

    SVNConfigFile.createDefaultConfiguration(configFile);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    FileUtil.delete(new File(myConfiguration.getConfigurationDirectory()));
  }

  public void testSavedAndRead() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

    final SVNException[] exception = new SVNException[1];
    final Boolean[] result = new Boolean[1];
    synchronousBackground(new Runnable() {
      @Override
      public void run() {
        try {

          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.save));

          commonScheme(url, false, null);
          Assert.assertEquals((SystemInfo.isWindows ? 3 : 2), listener.getCnt());
          //long start = System.currentTimeMillis();
          //waitListenerStep(start, listener, 3);

          SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          commonScheme(url, false, null);
          //start = System.currentTimeMillis();
          //waitListenerStep(start, listener, 4);
          Assert.assertEquals(4, listener.getCnt());
        }
        catch (SVNException e) {
          exception[0] = e;
        }
        result[0] = true;
      }
    });

    Assert.assertTrue(result[0]);
    myTestInteraction.assertNothing();
    Assert.assertEquals(4, listener.getCnt());
    listener.assertForAwt();
    savedOnceListener.assertForAwt();

    if (SystemInfo.isWindows) {
      savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);
    } else {
      savedOnceListener.assertNotSaved(url, ISVNAuthenticationManager.PASSWORD);
    }
    
    if (exception[0] != null) {
      throw exception[0];
    }
  }

  public void testSavedAndReadUnix() throws Exception {
    if (SystemInfo.isWindows) return;

    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

    final SVNException[] exception = new SVNException[1];
    final Boolean[] result = new Boolean[1];

    final File servers = new File(myConfiguration.getConfigurationDirectory(), "servers");
    final File oldServers = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.copy(servers, oldServers);
    try {
      FileUtil.appendToFile(servers, "\nstore-plaintext-passwords=yes\n");

      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.save));

            commonScheme(url, false, null);
            Assert.assertEquals(3, listener.getCnt());
            //long start = System.currentTimeMillis();
            //waitListenerStep(start, listener, 3);

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            commonScheme(url, false, null);
            //start = System.currentTimeMillis();
            //waitListenerStep(start, listener, 4);
            Assert.assertEquals(4, listener.getCnt());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });
    } finally {
      FileUtil.delete(servers);
      FileUtil.rename(oldServers, servers);
    }

    Assert.assertTrue(result[0]);
    myTestInteraction.assertNothing();
    Assert.assertEquals(4, listener.getCnt());
    listener.assertForAwt();
    savedOnceListener.assertForAwt();

    savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);

    if (exception[0] != null) {
      throw exception[0];
    }
  }

  private void waitListenerStep(long start, TestListener listener, final int stepNoNext) {
    while ((listener.getCnt() < stepNoNext) && ((System.currentTimeMillis() - start) < 10000)) {
      synchronized (mySynchObject) {
        try {
          mySynchObject.wait(50);
        }
        catch (InterruptedException e) {
          //
        }
      }
    }
    Assert.assertEquals(stepNoNext, listener.getCnt());
  }

  public void testWhenNotSaved() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    myInteractiveProvider.setSaveData(false);

    final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

    final SVNException[] exception = new SVNException[1];
    final Boolean[] result = new Boolean[1];
    synchronousBackground(new Runnable() {
      @Override
      public void run() {
        try {
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));

          commonScheme(url, false, null);
          //long start = System.currentTimeMillis();
          //waitListenerStep(start, listener, 2);
          Assert.assertEquals(2, listener.getCnt());

          savedOnceListener.assertNotSaved(url, ISVNAuthenticationManager.PASSWORD);
          // cause is not cleared though
          savedOnceListener.reset();

          SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          commonScheme(url, false, null);
          Assert.assertEquals(4, listener.getCnt());
          //start = System.currentTimeMillis();
          //waitListenerStep(start, listener, 4);
        }
        catch (SVNException e) {
          exception[0] = e;
        }
        result[0] = true;
      }
    });

    Assert.assertTrue(result[0]);
    myTestInteraction.assertNothing();
    Assert.assertEquals(4, listener.getCnt());
    listener.assertForAwt();
    savedOnceListener.assertForAwt();
    savedOnceListener.assertNotSaved(url, ISVNAuthenticationManager.PASSWORD);

    if (exception[0] != null) {
      throw exception[0];
    }
  }

  public void testWhenAuthCredsNoInConfig() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final File config = new File(myConfiguration.getConfigurationDirectory(), "config");
    final char[] chars = FileUtil.loadFileText(config);
    final String contents = String.valueOf(chars);
    final String auth = "[auth]";
    final int idx = contents.indexOf(auth);
    Assert.assertTrue(idx != -1);
    final String newContents = contents.substring(0, idx + auth.length()) + "\nstore-auth-creds=no\n" + contents.substring(idx + auth.length());

    final File oldConfig = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.rename(config, oldConfig);
    try {
      config.createNewFile();
      FileUtil.appendToFile(config, newContents);

      final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

      final SVNException[] exception = new SVNException[1];
      final Boolean[] result = new Boolean[1];
      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));

            commonScheme(url, false, null);
            Assert.assertEquals(2, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumAuthWarn());
            myTestInteraction.reset();
            savedOnceListener.assertForAwt();
            savedOnceListener.reset();

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            commonScheme(url, false, null);
            Assert.assertEquals(4, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumAuthWarn());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });

      Assert.assertTrue(result[0]);
      Assert.assertEquals(1, myTestInteraction.getNumAuthWarn());
      Assert.assertEquals(4, listener.getCnt());
      listener.assertForAwt();
      savedOnceListener.assertForAwt();
      savedOnceListener.assertNotSaved(url, ISVNAuthenticationManager.PASSWORD);

      if (exception[0] != null) {
        throw exception[0];
      }
    } finally {
      FileUtil.delete(config);
      FileUtil.rename(oldConfig, config);
    }
  }

  public void testWhenAuthCredsNoInServers() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final File servers = new File(myConfiguration.getConfigurationDirectory(), "servers");

    final File oldServers = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.copy(servers, oldServers);
    try {
      FileUtil.appendToFile(servers, "\nstore-auth-creds=no\n");

      final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

      final SVNException[] exception = new SVNException[1];
      final Boolean[] result = new Boolean[1];
      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));

            commonScheme(url, false, null);
            Assert.assertEquals(2, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumAuthWarn());
            myTestInteraction.reset();
            savedOnceListener.assertForAwt();
            savedOnceListener.reset();

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            commonScheme(url, false, null);
            Assert.assertEquals(4, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumAuthWarn());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });

      Assert.assertTrue(result[0]);
      Assert.assertEquals(1, myTestInteraction.getNumAuthWarn());
      Assert.assertEquals(4, listener.getCnt());
      listener.assertForAwt();
      savedOnceListener.assertForAwt();
      savedOnceListener.assertNotSaved(url, ISVNAuthenticationManager.PASSWORD);

      if (exception[0] != null) {
        throw exception[0];
      }
    } finally {
      FileUtil.delete(servers);
      FileUtil.rename(oldServers, servers);
    }
  }

  public void testWhenPassSaveNoInConfig() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final File config = new File(myConfiguration.getConfigurationDirectory(), "config");
    final char[] chars = FileUtil.loadFileText(config);
    final String contents = String.valueOf(chars);
    final String auth = "[auth]";
    final int idx = contents.indexOf(auth);
    Assert.assertTrue(idx != -1);
    final String newContents = contents.substring(0, idx + auth.length()) + "\nstore-passwords=no\n" + contents.substring(idx + auth.length());

    final File oldConfig = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.rename(config, oldConfig);
    try {
      config.createNewFile();
      FileUtil.appendToFile(config, newContents);

      final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

      final SVNException[] exception = new SVNException[1];
      final Boolean[] result = new Boolean[1];
      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));

            commonScheme(url, false, null);
            Assert.assertEquals(3, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
            myTestInteraction.reset();
            savedOnceListener.assertForAwt();
            savedOnceListener.reset();

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
            commonScheme(url, false, null);
            Assert.assertEquals(6, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });

      Assert.assertTrue(result[0]);
      Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
      Assert.assertEquals(6, listener.getCnt());
      listener.assertForAwt();
      savedOnceListener.assertForAwt();
      savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);

      if (exception[0] != null) {
        throw exception[0];
      }
    } finally {
      FileUtil.delete(config);
      FileUtil.rename(oldConfig, config);
    }
  }

  public void testWhenPassSaveNoInServers() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final File servers = new File(myConfiguration.getConfigurationDirectory(), "servers");

    final File oldServers = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.copy(servers, oldServers);
    try {
      FileUtil.appendToFile(servers, "\nstore-passwords=no\n");

      final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

      final SVNException[] exception = new SVNException[1];
      final Boolean[] result = new Boolean[1];
      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));

            commonScheme(url, false, null);
            Assert.assertEquals(3, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
            myTestInteraction.reset();
            savedOnceListener.assertForAwt();
            savedOnceListener.reset();

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
            commonScheme(url, false, null);
            Assert.assertEquals(6, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });

      Assert.assertTrue(result[0]);
      Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
      Assert.assertEquals(6, listener.getCnt());
      listener.assertForAwt();
      savedOnceListener.assertForAwt();
      savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);

      if (exception[0] != null) {
        throw exception[0];
      }
    } finally {
      FileUtil.delete(servers);
      FileUtil.rename(oldServers, servers);
    }
  }

  public void testWhenPassSaveNoForGroup() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final File servers = new File(myConfiguration.getConfigurationDirectory(), "servers");
    final char[] chars = FileUtil.loadFileText(servers);
    final String contents = String.valueOf(chars);
    final String groups = "[groups]";
    final int idx = contents.indexOf(groups);
    Assert.assertTrue(idx != -1);
    final String newContents = contents.substring(0, idx + groups.length()) + "\nsomegroup=some*\n" + contents.substring(idx + groups.length()) +
      "\n[somegroup]\nstore-passwords=no\n";

    final File oldServers = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.rename(servers, oldServers);
    try {
      servers.createNewFile();
      FileUtil.appendToFile(servers, newContents);

      final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

      final SVNException[] exception = new SVNException[1];
      final Boolean[] result = new Boolean[1];
      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));

            commonScheme(url, false, null);
            Assert.assertEquals(3, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
            myTestInteraction.reset();
            savedOnceListener.assertForAwt();
            savedOnceListener.reset();

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
            commonScheme(url, false, null);
            Assert.assertEquals(6, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });

      Assert.assertTrue(result[0]);
      Assert.assertEquals(1, myTestInteraction.getNumPasswordsWarn());
      Assert.assertEquals(6, listener.getCnt());
      listener.assertForAwt();
      savedOnceListener.assertForAwt();
      savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);

      if (exception[0] != null) {
        throw exception[0];
      }
    } finally {
      FileUtil.delete(servers);
      FileUtil.rename(oldServers, servers);
    }
  }

  public void testWhenPassPhraseSaveNo() throws Exception {
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);

    final File servers = new File(myConfiguration.getConfigurationDirectory(), "servers");

    final File oldServers = new File(myConfiguration.getConfigurationDirectory(), "config_old");
    FileUtil.copy(servers, oldServers);
    try {
      FileUtil.appendToFile(servers, "\nstore-ssl-client-cert-pp=no\n");

      final SVNURL url = SVNURL.parseURIEncoded("https://some.host.com/repo");

      final SVNException[] exception = new SVNException[1];
      final Boolean[] result = new Boolean[1];
      synchronousBackground(new Runnable() {
        @Override
        public void run() {
          try {
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));

            commonScheme(url, false, null);
            Assert.assertEquals(3, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumSSLWarn());
            myTestInteraction.reset();
            savedOnceListener.assertForAwt();
            savedOnceListener.reset();

            SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
            listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
            commonScheme(url, false, null);
            Assert.assertEquals(6, listener.getCnt());
            Assert.assertEquals(1, myTestInteraction.getNumSSLWarn());
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          result[0] = true;
        }
      });

      Assert.assertTrue(result[0]);
      Assert.assertEquals(1, myTestInteraction.getNumSSLWarn());
      Assert.assertEquals(6, listener.getCnt());
      listener.assertForAwt();
      savedOnceListener.assertForAwt();
      savedOnceListener.assertSaved(url, ISVNAuthenticationManager.SSL);

      if (exception[0] != null) {
        throw exception[0];
      }
    } finally {
      FileUtil.delete(servers);
      FileUtil.rename(oldServers, servers);
    }
  }

  public void testPlaintextPrompt() throws Exception {
    SVNJNAUtil.setJNAEnabled(false);

    // yes, no
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);
    myTestInteraction.setPlaintextAnswer(true);

    final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");

    final SVNException[] exception = new SVNException[1];
    final Boolean[] result = new Boolean[1];
    synchronousBackground(new Runnable() {
      @Override
      public void run() {
        try {
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.save));

          commonScheme(url, false, null);
          long start = System.currentTimeMillis();
          waitListenerStep(start, listener, 3);
          Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());
          savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);
          savedOnceListener.reset();
          myTestInteraction.reset();

          myConfiguration.clearAuthenticationDirectory();
          myTestInteraction.setPlaintextAnswer(false);
          SvnConfiguration.RUNTIME_AUTH_CACHE.clear();

          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
          commonScheme(url, false, null);
          start = System.currentTimeMillis();
          waitListenerStep(start, listener, 6);
          Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());

          savedOnceListener.reset();
          myTestInteraction.reset();
          SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
          commonScheme(url, false, null);
          start = System.currentTimeMillis();
          waitListenerStep(start, listener, 9);
          Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());
        }
        catch (SVNException e) {
          exception[0] = e;
        }
        result[0] = true;
      }
    });

    Assert.assertTrue(result[0]);
    Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());
    Assert.assertEquals(9, listener.getCnt());
    listener.assertForAwt();
    savedOnceListener.assertForAwt();
    savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);

    if (exception[0] != null) {
      throw exception[0];
    }
    SVNJNAUtil.setJNAEnabled(true);
  }

  public void testPlaintextPromptAndSecondPrompt() throws Exception {
    SVNJNAUtil.setJNAEnabled(false);

    // yes, no
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);
    myTestInteraction.setPlaintextAnswer(false);

    final SVNURL url = SVNURL.parseURIEncoded("http://some.host.com/repo");
    final SVNURL url2 = SVNURL.parseURIEncoded("http://some.other.host.com/repo");

    final SVNException[] exception = new SVNException[1];
    final Boolean[] result = new Boolean[1];
    synchronousBackground(new Runnable() {
      @Override
      public void run() {
        try {
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));

          commonScheme(url, false, null);
          long start = System.currentTimeMillis();
          waitListenerStep(start, listener, 3);
          Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());
          // actually password not saved, but save was called
          savedOnceListener.assertSaved(url, ISVNAuthenticationManager.PASSWORD);
          savedOnceListener.reset();
          myTestInteraction.reset();

          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url2, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url2, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url2, Type.without_pasword_save));
          commonScheme(url2, false, "anotherRealm");
          start = System.currentTimeMillis();
          waitListenerStep(start, listener, 6);
          Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());
        }
        catch (SVNException e) {
          exception[0] = e;
        }
        result[0] = true;
      }
    });

    Assert.assertTrue(result[0]);
    Assert.assertEquals(1, myTestInteraction.getNumPlaintextPrompt());
    Assert.assertEquals(6, listener.getCnt());
    listener.assertForAwt();
    savedOnceListener.assertForAwt();
    // didn't called to save for 2nd time
    savedOnceListener.assertNotSaved(url, ISVNAuthenticationManager.PASSWORD);

    if (exception[0] != null) {
      throw exception[0];
    }
    SVNJNAUtil.setJNAEnabled(true);
  }

  public void testPlaintextSSLPrompt() throws Exception {
    SVNJNAUtil.setJNAEnabled(false);

    // yes, no
    final TestListener listener = new TestListener(mySynchObject);
    myAuthenticationManager.addListener(listener);
    final SavedOnceListener savedOnceListener = new SavedOnceListener();
    myAuthenticationManager.addListener(savedOnceListener);
    myTestInteraction.setSSLPlaintextAnswer(true);

    final SVNURL url = SVNURL.parseURIEncoded("https://some.host.com/repo");

    final SVNException[] exception = new SVNException[1];
    final Boolean[] result = new Boolean[1];
    synchronousBackground(new Runnable() {
      @Override
      public void run() {
        try {
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.save));

          commonScheme(url, false, null);
          long start = System.currentTimeMillis();
          waitListenerStep(start, listener, 3);
          Assert.assertEquals(1, myTestInteraction.getNumSSLPlaintextPrompt());
          savedOnceListener.assertSaved(url, ISVNAuthenticationManager.SSL);
          savedOnceListener.reset();
          myTestInteraction.reset();

          myConfiguration.clearAuthenticationDirectory();
          myTestInteraction.setSSLPlaintextAnswer(false);
          SvnConfiguration.RUNTIME_AUTH_CACHE.clear();

          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
          commonScheme(url, false, null);
          start = System.currentTimeMillis();
          waitListenerStep(start, listener, 6);
          Assert.assertEquals(1, myTestInteraction.getNumSSLPlaintextPrompt());

          SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
          myTestInteraction.reset();
          savedOnceListener.reset();

          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.interactive, url, Type.request));
          listener.addStep(new Trinity<ProviderType, SVNURL, Type>(ProviderType.persistent, url, Type.without_pasword_save));
          commonScheme(url, false, null);
          start = System.currentTimeMillis();
          waitListenerStep(start, listener, 9);
          Assert.assertEquals(1, myTestInteraction.getNumSSLPlaintextPrompt());
        }
        catch (SVNException e) {
          exception[0] = e;
        }
        result[0] = true;
      }
    });

    Assert.assertTrue(result[0]);
    Assert.assertEquals(1, myTestInteraction.getNumSSLPlaintextPrompt());
    Assert.assertEquals(9, listener.getCnt());
    listener.assertForAwt();
    savedOnceListener.assertForAwt();
    savedOnceListener.assertSaved(url, ISVNAuthenticationManager.SSL);

    if (exception[0] != null) {
      throw exception[0];
    }
    SVNJNAUtil.setJNAEnabled(true);
  }

  private void synchronousBackground(final Runnable runnable) throws InterruptedException {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        } finally {
          semaphore.up();
        }
      }
    });
    semaphore.waitFor();
  }

  private void commonScheme(final SVNURL url, final boolean username, final String realm) throws SVNException {
    String kind = null;

    final String actualRealm = realm == null ? "realm" : realm;
    final String protocol = url.getProtocol();
    if (username) {
      kind = ISVNAuthenticationManager.USERNAME;
    } else if ("svn+ssh".equals(protocol)) {
      kind = ISVNAuthenticationManager.SSH;
    } else if ("http".equals(protocol)) {
      kind = ISVNAuthenticationManager.PASSWORD;
    } else if ("https".equals(protocol)) {
      kind = ISVNAuthenticationManager.SSL;
    } else if ("file".equals(protocol)) {
      kind = ISVNAuthenticationManager.USERNAME;
    }
    SVNAuthentication authentication = null;
    try {
      authentication = myAuthenticationManager.getFirstAuthentication(kind, actualRealm, url);
      while (! passwordSpecified(authentication)) {
        authentication = myAuthenticationManager.getNextAuthentication(kind, actualRealm, url);
      }
    } finally {
      myAuthenticationManager.acknowledgeAuthentication(authentication != null, kind, actualRealm, null, authentication);
    }
  }

  private static class SvnTestInteractiveAuthentication implements ISVNAuthenticationProvider {
    private final SvnAuthenticationManager myManager;
    private boolean mySaveData;

    public SvnTestInteractiveAuthentication(SvnAuthenticationManager manager) {
      myManager = manager;
      mySaveData = true;
    }

    public void setSaveData(boolean saveData) {
      mySaveData = saveData;
    }

    @Override
    public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
      return ISVNAuthenticationProvider.REJECTED;
    }

    @Override
    public SVNAuthentication requestClientAuthentication(String kind,
                                                         SVNURL url,
                                                         String realm,
                                                         SVNErrorMessage errorMessage,
                                                         SVNAuthentication previousAuth,
                                                         boolean authMayBeStored) {
      authMayBeStored = authMayBeStored & mySaveData;
      SVNAuthentication result = null;
      if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
        result = new SVNUserNameAuthentication("username", authMayBeStored);
      } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
        result = new SVNPasswordAuthentication("username", "abc", authMayBeStored, url, false);
      } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
        result = new SVNSSHAuthentication("username", "abc", -1, authMayBeStored, url, false);
      } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
        result = new SVNSSLAuthentication(new File("aaa"), "abc", authMayBeStored, url, false);
      }
      if (! ISVNAuthenticationManager.USERNAME.equals(kind)) {
        myManager.requested(ProviderType.interactive, url, realm, kind, result == null);
      }
      return result;
    }
  }

  private static boolean passwordSpecified(final SVNAuthentication authentication) {
    final String kind = authentication.getKind();
    if (ISVNAuthenticationManager.SSH.equals(kind)) {
      if (((SVNSSHAuthentication) authentication).hasPrivateKey()) {
        return ((SVNSSHAuthentication) authentication).getPassphrase() != null &&
               ((((SVNSSHAuthentication) authentication).getPrivateKey() != null) || (((SVNSSHAuthentication) authentication).getPrivateKeyFile() != null));
      } else {
        return ((SVNSSHAuthentication) authentication).getPassword() != null;
      }
    } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
      return ((SVNPasswordAuthentication) authentication).getPassword() != null;
    } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
      return ((SVNSSLAuthentication) authentication).getPassword() != null;
    } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
      return true;
    }
    return true;
  }

  private static class TestListener implements SvnAuthenticationListener {
    private List<Trinity<ProviderType, SVNURL, Type>> myExpectedSequence;
    private int myCnt;
    private final Object mySynchObject;
    private boolean mySuccess;

    private TestListener(final Object synchObject) {
      mySynchObject = synchObject;
      myExpectedSequence = new ArrayList<Trinity<ProviderType, SVNURL, Type>>();
      myCnt = 0;
      mySuccess = true;
    }

    public void addStep(Trinity<ProviderType, SVNURL, Type> step) {
      myExpectedSequence.add(step);
    }

    public int getCnt() {
      return myCnt;
    }

    @Override
    public void actualSaveWillBeTried(ProviderType type, SVNURL url, String realm, String kind, boolean withCredentials) {
      if (! mySuccess) return;
      
      mySuccess = myExpectedSequence.get(myCnt).equals(new Trinity<ProviderType, SVNURL, Type>(type, url, withCredentials ? Type.save : Type.without_pasword_save));
      if (mySuccess) {
        ++ myCnt;
      }
      synchronized (mySynchObject) {
        mySynchObject.notifyAll();
      }
    }

    @Override
    public void requested(ProviderType type, SVNURL url, String realm, String kind, boolean canceled) {
      if (! mySuccess) return;

      mySuccess = myExpectedSequence.get(myCnt).equals(new Trinity<ProviderType, SVNURL, Type>(type, url, Type.request));
      if (mySuccess) {
        ++ myCnt;
      }
      synchronized (mySynchObject) {
        mySynchObject.notifyAll();
      }
    }

    public void assertForAwt() {
      Assert.assertTrue("last cnt = " + myCnt, mySuccess);
    }
  }

  private static class SavedOnceListener implements SvnAuthenticationListener {
    private final Set<Pair<SVNURL, String>> myClientRequested;
    private final Set<Pair<SVNURL, String>> mySaved;
    private String myCause;

    private SavedOnceListener() {
      myClientRequested = new HashSet<Pair<SVNURL, String>>();
      mySaved = new HashSet<Pair<SVNURL, String>>();
    }

    public void reset() {
      mySaved.clear();
      myClientRequested.clear();
    }

    @Override
    public void actualSaveWillBeTried(ProviderType type, SVNURL url, String realm, String kind, boolean withCredentials) {
      final Pair<SVNURL, String> pair = new Pair<SVNURL, String>(url, kind);
      if (mySaved.contains(pair)) {
        myCause = "saved twice";
      }
      mySaved.add(pair);
    }

    public boolean isSaved(final SVNURL url, final String kind) {
      return mySaved.contains(new Pair<SVNURL, String>(url, kind));
    }

    @Override
    public void requested(ProviderType type, SVNURL url, String realm, String kind, boolean canceled) {
      if (ProviderType.interactive.equals(type)) {
        final Pair<SVNURL, String> pair = new Pair<SVNURL, String>(url, kind);
        if (myClientRequested.contains(pair)) {
          myCause = "client requested twice";
        }
        myClientRequested.add(pair);
      }
    }

    public void assertForAwt() {
      Assert.assertTrue(myCause, myCause == null);
    }

    public void assertSaved(final SVNURL url, final String kind) {
      Assert.assertTrue("not saved", mySaved.contains(new Pair<SVNURL, String>(url, kind)));
    }

    public void assertNotSaved(final SVNURL url, final String kind) {
      Assert.assertTrue("saved", ! mySaved.contains(new Pair<SVNURL, String>(url, kind)));
    }
  }

  private static enum Type {
    request,
    save,
    without_pasword_save
  }

  private static class TestInteraction implements SvnAuthenticationInteraction {
    private int myNumAuthWarn;
    private int myNumPasswordsWarn;
    private int myNumSSLWarn;
    private int myNumPlaintextPrompt;
    private int myNumSSLPlaintextPrompt;

    private boolean myPlaintextAnswer;
    private boolean mySSLPlaintextAnswer;

    public void setPlaintextAnswer(boolean plaintextAnswer) {
      myPlaintextAnswer = plaintextAnswer;
    }

    public void setSSLPlaintextAnswer(boolean SSLPlaintextAnswer) {
      mySSLPlaintextAnswer = SSLPlaintextAnswer;
    }

    public void assertNothing() {
      Assert.assertEquals("myNumAuthWarn", myNumAuthWarn, 0);
      Assert.assertEquals("myNumPasswordsWarn", myNumPasswordsWarn, 0);
      Assert.assertEquals("myNumSSLWarn", myNumSSLWarn, 0);
      Assert.assertEquals("myNumPlaintextPrompt", myNumPlaintextPrompt, 0);
      Assert.assertEquals("myNumSSLPlaintextPrompt", myNumSSLPlaintextPrompt, 0);
    }

    public void reset() {
      myNumAuthWarn = 0;
      myNumPasswordsWarn = 0;
      myNumSSLWarn = 0;
      myNumPlaintextPrompt = 0;
      myNumSSLPlaintextPrompt = 0;
    }

    @Override
    public boolean promptForPlaintextPasswordSaving(SVNURL url, String realm) {
      ++ myNumPlaintextPrompt;
      return myPlaintextAnswer;
    }

    @Override
    public boolean promptInAwt() {
      return false;
    }

    @Override
    public void warnOnAuthStorageDisabled(SVNURL url) {
      ++ myNumAuthWarn;
    }

    @Override
    public void warnOnPasswordStorageDisabled(SVNURL url) {
      ++ myNumPasswordsWarn;
    }

    @Override
    public void warnOnSSLPassphraseStorageDisabled(SVNURL url) {
      ++ myNumSSLWarn;
    }

    @Override
    public boolean promptForSSLPlaintextPassphraseSaving(SVNURL url, String realm, File certificateFile) {
      ++ myNumSSLPlaintextPrompt;
      return mySSLPlaintextAnswer;
    }

    public int getNumAuthWarn() {
      return myNumAuthWarn;
    }

    public int getNumPasswordsWarn() {
      return myNumPasswordsWarn;
    }

    public int getNumPlaintextPrompt() {
      return myNumPlaintextPrompt;
    }

    public int getNumSSLPlaintextPrompt() {
      return myNumSSLPlaintextPrompt;
    }

    public int getNumSSLWarn() {
      return myNumSSLWarn;
    }
  }
}
