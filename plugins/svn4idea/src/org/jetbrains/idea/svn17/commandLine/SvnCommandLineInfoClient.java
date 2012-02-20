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
package org.jetbrains.idea.svn17.commandLine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import org.jetbrains.idea.svn17.SvnVcs17;
import org.jetbrains.idea.svn17.portable.SvnExceptionWrapper;
import org.jetbrains.idea.svn17.portable.SvnkitSvnWcClient;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.xml.sax.SAXException;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 12:59 PM
 */
public class SvnCommandLineInfoClient extends SvnkitSvnWcClient {
  private final Project myProject;

  public SvnCommandLineInfoClient(final Project project) {
    super(SvnVcs17.getInstance(project).createWCClient());
    myProject = project;
  }

  @Override
  public void doInfo(File path, SVNRevision revision, boolean recursive, ISVNInfoHandler handler) throws SVNException {
    doInfo(path, SVNRevision.UNDEFINED, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null, handler);
  }

  @Override
  public void doInfo(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    doInfo(path, pegRevision, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, null, handler);
  }

  @Override
  public void doInfo(File path,
                     SVNRevision pegRevision,
                     SVNRevision revision,
                     SVNDepth depth,
                     Collection changeLists,
                     final ISVNInfoHandler handler) throws SVNException {
    File base = path.isDirectory() ? path : path.getParentFile();
    base = correctUpToExistingParent(base);
    if (base == null) {
      // very unrealistic
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), new RuntimeException("Can not find existing parent file"));
    }
    final SvnSimpleCommand command = new SvnSimpleCommand(myProject, base, SvnCommandName.info);

    if (depth != null) {
      command.addParameters("--depth", depth.getName());
    }
    if (revision != null && ! SVNRevision.UNDEFINED.equals(revision) && ! SVNRevision.WORKING.equals(revision)) {
      command.addParameters("-r", revision.toString());
    }
    command.addParameters("--xml");
    SvnCommandLineStatusClient.changelistsToCommand(changeLists, command);
    if (pegRevision != null && ! SVNRevision.UNDEFINED.equals(pegRevision) && ! SVNRevision.WORKING.equals(pegRevision)) {
      command.addParameters(path.getPath() + "@" + pegRevision.toString());
    } else {
      command.addParameters(path.getPath());
    }

    final SvnInfoHandler[] infoHandler = new SvnInfoHandler[1];
    infoHandler[0] = new SvnInfoHandler(base, new Consumer<SVNInfo>() {
      @Override
      public void consume(SVNInfo info) {
        try {
          handler.handleInfo(info);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    });

    try {
      final String result = command.run();
      // todo not synchronized wrapper stream!
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(new StringBufferInputStream(result), infoHandler[0]);

    }
    catch (SvnExceptionWrapper e) {
      throw (SVNException) e.getCause();
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    catch (ParserConfigurationException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    catch (SAXException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    catch (VcsException e) {
      final String text = e.getMessage();
      if (!StringUtil.isEmptyOrSpaces(text) && text.contains("W155010")) {
        // just null
        return;
      }
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  private File correctUpToExistingParent(File base) {
    while (base != null) {
      if (base.exists()) return base;
      base = base.getParentFile();
    }
    return null;
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
    throws SVNException {
    doInfo(url, pegRevision, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, handler);
  }

  @Override
  public void doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth, ISVNInfoHandler handler)
    throws SVNException {
    throw new NotImplementedException();
  }

  @Override
  public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
    final SVNInfo[] infoArr = new SVNInfo[1];
    doInfo(path, SVNRevision.UNDEFINED, revision, SVNDepth.EMPTY, null, new ISVNInfoHandler() {
      @Override
      public void handleInfo(SVNInfo info) throws SVNException {
        infoArr[0] = info;
      }
    });
    return infoArr[0];
  }

  @Override
  public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
    final SVNInfo[] infoArr = new SVNInfo[1];
    doInfo(url, pegRevision, revision, SVNDepth.EMPTY, new ISVNInfoHandler() {
      @Override
      public void handleInfo(SVNInfo info) throws SVNException {
        infoArr[0] = info;
      }
    });
    return infoArr[0];
  }
}
