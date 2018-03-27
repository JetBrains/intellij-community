// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.*;
import org.jetbrains.idea.svn.info.Info;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.*;
import static org.jetbrains.idea.svn.commandLine.CommandUtil.requireExistingParent;

public class CmdStatusClient extends BaseSvnClient implements StatusClient {

  @Override
  public long doStatus(@NotNull File path,
                       @Nullable Revision revision,
                       @NotNull Depth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       @NotNull StatusConsumer handler) throws SvnBindException {
    File base = requireExistingParent(path);
    Info infoBase = myFactory.createInfoClient().doInfo(base, revision);
    List<String> parameters = newArrayList();

    putParameters(parameters, path, depth, remote, reportAll, includeIgnored);

    CommandExecutor command = execute(myVcs, Target.on(path), SvnCommandName.st, parameters, null);
    parseResult(path, revision, handler, base, infoBase, command);
    return 0;
  }

  private void parseResult(@NotNull File path,
                           @Nullable Revision revision,
                           @NotNull StatusConsumer handler,
                           @NotNull File base,
                           @Nullable Info infoBase,
                           @NotNull CommandExecutor command) throws SvnBindException {
    String result = command.getOutput();

    if (StringUtil.isEmptyOrSpaces(result)) {
      throw new SvnBindException("Status request returned nothing for command: " + command.getCommandText());
    }

    try {
      Ref<SvnStatusHandler> parsingHandler = Ref.create();
      parsingHandler.set(createStatusHandler(revision, handler, base, infoBase, () -> parsingHandler.get().getPending()));
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(new ByteArrayInputStream(result.trim().getBytes(CharsetToolkit.UTF8_CHARSET)), parsingHandler.get());
      if (!parsingHandler.get().isAnythingReported()) {
        if (!isSvnVersioned(myVcs, path)) {
          throw new SvnBindException(ErrorCode.WC_NOT_WORKING_COPY, "Command - " + command.getCommandText() + ". Result - " + result);
        } else {
          // return status indicating "NORMAL" state
          // typical output would be like
          // <status>
          // <target path="1.txt"></target>
          // </status>
          // so it does not contain any <entry> element and current parsing logic returns null

          PortableStatus status = new PortableStatus();
          status.setFile(path);
          status.setPath(path.getAbsolutePath());
          status.setContentsStatus(StatusType.STATUS_NORMAL);
          status.setInfoGetter(() -> createInfoGetter(null).convert(path));
          handler.consume(status);
        }
      }
    }
    catch (SvnExceptionWrapper e) {
      throw new SvnBindException(e.getCause());
    } catch (IOException | ParserConfigurationException e) {
      throw new SvnBindException(e);
    }
    catch (SAXException e) {
      // status parsing errors are logged separately as sometimes there are parsing errors connected to terminal output handling.
      // these errors primarily occur when status output is rather large.
      // and status output could be large, for instance, when working copy is locked (seems that each file is listed in status output).
      command.logCommand();
      throw new SvnBindException(e);
    }
  }

  private static void putParameters(@NotNull List<String> parameters,
                                    @NotNull File path,
                                    @Nullable Depth depth,
                                    boolean remote,
                                    boolean reportAll,
                                    boolean includeIgnored) {
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, remote, "-u");
    CommandUtil.put(parameters, reportAll, "--verbose");
    CommandUtil.put(parameters, includeIgnored, "--no-ignore");
    parameters.add("--xml");
  }

  @NotNull
  public SvnStatusHandler createStatusHandler(@Nullable Revision revision,
                                              @NotNull StatusConsumer handler,
                                              @NotNull File base,
                                              @Nullable Info infoBase,
                                              @NotNull Supplier<PortableStatus> statusSupplier) {
    SvnStatusHandler.ExternalDataCallback callback = createStatusCallback(handler, base, infoBase, statusSupplier);

    return new SvnStatusHandler(callback, base, createInfoGetter(revision));
  }

  @NotNull
  private Convertor<File, Info> createInfoGetter(@Nullable Revision revision) {
    return file -> {
      try {
        return myFactory.createInfoClient().doInfo(file, revision);
      }
      catch (SvnBindException e) {
        throw new SvnExceptionWrapper(e);
      }
    };
  }

  @NotNull
  public static SvnStatusHandler.ExternalDataCallback createStatusCallback(@NotNull StatusConsumer handler,
                                                                           @NotNull File base,
                                                                           @Nullable Info infoBase,
                                                                           @NotNull Supplier<PortableStatus> statusSupplier) {
    Map<File, Info> externalsMap = newHashMap();
    Ref<String> changelistName = Ref.create();

    return new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        PortableStatus pending = statusSupplier.get();
        pending.setChangelistName(changelistName.get());
        try {
          File pendingFile = new File(pending.getPath());
          File externalsBase = find(externalsMap.keySet(), file -> isAncestor(file, pendingFile, false));
          File baseFile = notNull(externalsBase, base);
          Info baseInfo = externalsBase != null ? externalsMap.get(externalsBase) : infoBase;

          if (baseInfo != null) {
            pending.setURL(pendingFile.isAbsolute()
                           ? append(baseInfo.getURL(), getRelativePath(baseFile.getPath(), pending.getPath()))
                           : append(baseInfo.getURL(), toSystemIndependentName(pending.getPath())));
          }
          if (StatusType.STATUS_EXTERNAL.equals(pending.getNodeStatus())) {
            externalsMap.put(pending.getFile(), pending.getInfo());
          }
          handler.consume(pending);
        }
        catch (SvnBindException e) {
          throw new SvnExceptionWrapper(e);
        }
      }

      @Override
      public void switchChangeList(String newList) {
        changelistName.set(newList);
      }
    };
  }

  @Override
  public Status doStatus(@NotNull File path, boolean remote) throws SvnBindException {
    Ref<Status> status = Ref.create();
    doStatus(path, Revision.UNDEFINED, Depth.EMPTY, remote, false, false, false, status::set);
    return status.get();
  }
}
