// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.platform.eel.EelPlatform;
import com.intellij.sh.ShNotificationDisplayIds;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.utils.ExternalServicesUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.Decompressor;
import com.intellij.util.text.SemVer;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.intellij.platform.eel.EelPlatformKt.*;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.EelProviderUtil.toEelApiBlocking;
import static com.intellij.sh.ShBundle.message;
import static com.intellij.sh.ShBundle.messagePointer;
import static com.intellij.sh.ShNotification.NOTIFICATION_GROUP;
import static com.intellij.sh.statistics.ShCounterUsagesCollector.EXTERNAL_ANNOTATOR_DOWNLOADED_EVENT_ID;

public final class ShShellcheckUtil {
  private static final Logger LOG = Logger.getInstance(ShShellcheckUtil.class);

  private static final Key<Boolean> UPDATE_NOTIFICATION_SHOWN = Key.create("SHELLCHECK_UPDATE");
          static final @NlsSafe String SHELLCHECK = "shellcheck";
  private static final String SHELLCHECK_VERSION = "0.10.0";
  private static final String SHELLCHECK_ARTIFACT_VERSION = SHELLCHECK_VERSION + "-1";
  private static final String SHELLCHECK_ARCHIVE_EXTENSION = ".tar.gz";
  private static final String SHELLCHECK_URL =
    "https://cache-redirector.jetbrains.com/intellij-dependencies/org/jetbrains/intellij/deps/shellcheck/shellcheck/";

  public static void download(@NotNull Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    download(project, onSuccess, onFailure, false);
  }

  @VisibleForTesting
  public static @NotNull String spellcheckBin(@NotNull EelPlatform platform) {
    return isWindows(platform) ? SHELLCHECK + ".exe" : SHELLCHECK;
  }

  private static void download(@NotNull Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure, boolean withReplace) {
    Task.Backgroundable task = new Task.Backgroundable(project, message("sh.shellcheck.download.label.text")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final var eelDescriptor = getEelDescriptor(project);
        final var eel = toEelApiBlocking(eelDescriptor);

        final var downloadPath = ExternalServicesUtil.computeDownloadPath(eel);

        if (!Files.exists(downloadPath)) {
          try {
            Files.createDirectory(downloadPath);
          }
          catch (IOException e) {
            //
          }
        }
        final var shellcheck = downloadPath.resolve(spellcheckBin(eel.getPlatform()));
        final var oldShellcheck = downloadPath.resolve("old_" + spellcheckBin(eel.getPlatform()));

        if (Files.exists(shellcheck)) {
          if (withReplace) {
            boolean successful = renameOldShellcheck(shellcheck.toFile(), oldShellcheck.toFile(), onFailure);
            if (!successful) return;
          }
          else {
            setupShellcheckPath(project, shellcheck.toFile(), onSuccess, onFailure);
            return;
          }
        }

        String url = getShellcheckDistributionLink(eel.getPlatform());
        if (StringUtil.isEmpty(url)) {
          LOG.debug("Unsupported OS for shellcheck");
          return;
        }

        String downloadName = SHELLCHECK + SHELLCHECK_ARCHIVE_EXTENSION;
        DownloadableFileService service = DownloadableFileService.getInstance();
        DownloadableFileDescription description = service.createFileDescription(url, downloadName);
        FileDownloader downloader = service.createDownloader(Collections.singletonList(description), downloadName);

        try {
          final var pairs = downloader.download(downloadPath.toFile());
          final var first = ContainerUtil.getFirstItem(pairs);
          final var file = first != null ? first.first : null;
          if (file != null) {
            String path = decompressShellcheck(file.toPath(), downloadPath, eel.getPlatform());
            if (Strings.isNotEmpty(path)) {
              FileUtil.setExecutable(new File(path));
              ShSettings.setShellcheckPath(project, path);
              if (withReplace) {
                LOG.debug("Remove old shellcheck");
                FileUtil.delete(oldShellcheck);
              }
              ApplicationManager.getApplication().invokeLater(onSuccess);
              EXTERNAL_ANNOTATOR_DOWNLOADED_EVENT_ID.log();
            }
          }
        }
        catch (IOException e) {
          LOG.warn("Can't download shellcheck", e);
          if (withReplace) rollbackToOldShellcheck(shellcheck.toFile(), oldShellcheck.toFile());
          ApplicationManager.getApplication().invokeLater(onFailure);
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private static void setupShellcheckPath(@NotNull Project project, @NotNull File shellcheck, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    try {
      String path = ShSettings.getShellcheckPath(project);
      String shellcheckPath = shellcheck.getPath();
      if (StringUtil.isNotEmpty(path) && path.equals(shellcheckPath)) {
        LOG.debug("Shellcheck already downloaded");
      }
      else {
        ShSettings.setShellcheckPath(project, shellcheckPath);
      }
      if (!shellcheck.canExecute()) FileUtil.setExecutable(shellcheck);
      ApplicationManager.getApplication().invokeLater(onSuccess);
    }
    catch (IOException e) {
      LOG.warn("Can't evaluate shellcheck path or make it executable", e);
      ApplicationManager.getApplication().invokeLater(onFailure);
    }
  }

  private static boolean renameOldShellcheck(@NotNull File shellcheck, @NotNull File oldShellcheck, @NotNull Runnable onFailure) {
    LOG.info("Rename shellcheck to the temporary filename");
    try {
      FileUtil.rename(shellcheck, oldShellcheck);
    }
    catch (IOException e) {
      LOG.info("Can't rename shellcheck to the temporary filename", e);
      ApplicationManager.getApplication().invokeLater(onFailure);
      return false;
    }
    return true;
  }

  private static void rollbackToOldShellcheck(@NotNull File shellcheck, @NotNull File oldShellcheck) {
    LOG.info("Update failed, rollback");
    try {
      FileUtil.rename(oldShellcheck, shellcheck);
    }
    catch (IOException e) {
      LOG.info("Can't rollback shellcheck after failed update", e);
    }
    FileUtil.delete(oldShellcheck);
  }

  @VisibleForTesting
  public static boolean isExecutionValidPath(@Nullable String path) {
    if (path == null || ShSettings.I_DO_MIND_SUPPLIER.get().equals(path)) return false;
    File file = new File(path);
    return file.canExecute() && file.getName().contains(SHELLCHECK);
  }

  @ApiStatus.Internal
  public static boolean isValidPath(@Nullable String path) {
    if (path == null) return false;
    if (ShSettings.I_DO_MIND_SUPPLIER.get().equals(path)) return true;
    File file = new File(path);
    return file.canExecute() && file.getName().contains(SHELLCHECK);
  }

  @ApiStatus.Internal
  public static void checkShellCheckForUpdate(@NotNull Project project) {
    Application application = ApplicationManager.getApplication();
    if (application.getUserData(UPDATE_NOTIFICATION_SHOWN) != null) return;
    application.putUserData(UPDATE_NOTIFICATION_SHOWN, true);

    if (application.isDispatchThread()) {
      application.executeOnPooledThread(() -> checkForUpdateInBackgroundThread(project));
    } else {
      checkForUpdateInBackgroundThread(project);
    }
  }

  @RequiresBackgroundThread
  private static void checkForUpdateInBackgroundThread(@NotNull Project project) {
    Pair<String, String> newVersionAvailable = getVersionUpdate(project);
    if (newVersionAvailable == null) return;

    String currentVersion = newVersionAvailable.first;
    String newVersion = newVersionAvailable.second;

    Notification notification = NOTIFICATION_GROUP.createNotification(
      message("sh.shell.script"),
      message("sh.shellcheck.update.question", currentVersion, newVersion),
      NotificationType.INFORMATION);
    notification.setDisplayId(ShNotificationDisplayIds.UPDATE_SHELLCHECK);
    notification.setSuggestionType(true);
    notification.addAction(
      NotificationAction.createSimple(messagePointer("sh.update"), () -> {
        notification.expire();
        download(project,
                 () -> NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.shellcheck.success.update"),
                                                             NotificationType.INFORMATION)
                   .setDisplayId(ShNotificationDisplayIds.UPDATE_SHELLCHECK_SUCCESS)
                   .notify(project),
                 () -> NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.shellcheck.cannot.update"),
                                                             NotificationType.ERROR)
                   .setDisplayId(ShNotificationDisplayIds.UPDATE_SHELLCHECK_ERROR)
                   .notify(project),
                 true);
      }));
    notification.addAction(NotificationAction.createSimple(messagePointer("sh.skip.version"), () -> {
      notification.expire();
      ShSettings.setSkippedShellcheckVersion(SHELLCHECK_VERSION);
    }));
    notification.notify(project);
  }

  /**
   * @return pair of old and new versions or null if there's no update
   */
  private static Pair<String, String> getVersionUpdate(@NotNull Project project) {
    final String updateVersion = SHELLCHECK_VERSION;
    final SemVer updateVersionVer = SemVer.parseFromText(updateVersion);
    if (updateVersionVer == null) return null;
    if (ShSettings.getSkippedShellcheckVersion().equals(updateVersion)) return null;

    String path = ShSettings.getShellcheckPath(project);
    if (ShSettings.I_DO_MIND_SUPPLIER.get().equals(path)) return null;
    File file = new File(path);
    if (!file.canExecute()) return null;
    if (!file.getName().contains(SHELLCHECK)) return null;
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("--version");
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);

      String stdout = processOutput.getStdout();
      String current = getVersionFromStdOut(stdout);
      if (current == null) {
        current = "unknown";
        return Pair.create(current, updateVersion);
      }
      SemVer currentVersion = SemVer.parseFromText(current);
      if (currentVersion == null || updateVersionVer.isGreaterThan(currentVersion)) {
        return Pair.create(current, updateVersion);
      }
      return null;
    }
    catch (ExecutionException e) {
      LOG.debug("Exception in process execution", e);
    }
    return null;
  }

  private static String getVersionFromStdOut(String stdout) {
    String[] lines = StringUtil.splitByLines(stdout);
    for (String line : lines) {
      line = line.trim().toLowerCase(Locale.ENGLISH);
      String prefix = "version:";
      if (line.contains(prefix)) {
        return line.substring(prefix.length()).trim();
      }
    }
    return null;
  }

  public static @NotNull String decompressShellcheck(Path tarPath, Path directory, @NotNull EelPlatform eelPlatform) throws IOException {
    new Decompressor.Tar(tarPath).extract(directory);

    // cleaning up
    NioFiles.deleteRecursively(tarPath);

    File shellcheck = new File(directory.toFile(), spellcheckBin(eelPlatform));
    return shellcheck.exists() ? shellcheck.getPath() : "";
  }

  @VisibleForTesting
  public static @NlsSafe @Nullable String getShellcheckDistributionLink(@NotNull EelPlatform platform) {
    String platformString = isMac(platform) ? "mac" : isWindows(platform) ? "windows" : isLinux(platform) ? "linux" : null;
    if (platformString == null) return null;
    String arch = isArm64(platform) ? "arm64" : "amd64";
    if (platformString.equals("windows") && arch.equals("arm64")) {
      // Unsupported OS + Arch
      return null;
    }
    return SHELLCHECK_URL +
           SHELLCHECK_ARTIFACT_VERSION +
           "/shellcheck-" + SHELLCHECK_ARTIFACT_VERSION + '-' + platformString + '-' + arch + SHELLCHECK_ARCHIVE_EXTENSION;
  }

  @SuppressWarnings("SpellCheckingInspection")
  public static final Map<@NlsSafe String, @Nls String> SHELLCHECK_CODES = new TreeMap<>(){{
    put("SC1000", message("check1000.is.not.used.specially.and.should.therefore.be.escaped"));
    put("SC1001", message("check1001.this.o.will.be.a.regular.o.in.this.context"));
    put("SC1003", message("check1003.want.to.escape.a.single.quote.echo.this.is.how.it.s.done"));
    put("SC1004", message("check1004.this.backslash.linefeed.is.literal.break.outside.single.quotes.if.you.just.want.to.break.the.line"));
    put("SC1007", message("check1007.remove.space.after.if.trying.to.assign.a.value.or.for.empty.string.use.var"));
    put("SC1008", message("check.1008.this.shebang.was.unrecognized.shellcheck.only.supports.sh.bash.dash.ksh.add.a.shell.directive.to.specify"));
    put("SC1009", message("check.1009.the.mentioned.parser.error.was.in"));
    put("SC1010", message("check.1010.use.semicolon.or.linefeed.before.done.or.quote.to.make.it.literal"));
    put("SC1011", message("check.1011.this.apostrophe.terminated.the.single.quoted.string"));
    put("SC1012", message("check.1012.is.just.literal.t.here.for.tab.use.printf.instead"));
    put("SC1014", message("check.1014.use.if.cmd.then.to.check.exit.code.or.if.cmd.to.check.output"));
    put("SC1015", message("check.1015.this.is.a.unicode.double.quote.delete.and.retype.it"));
    put("SC1016", message("check.1016.this.is.a.unicode.single.quote.delete.and.retype.it"));
    put("SC1017", message("check.1017.literal.carriage.return.run.script.through.tr.d"));
    put("SC1018", message("check.1018.this.is.a.unicode.non.breaking.space.delete.it.and.retype.as.space"));
    put("SC1019", message("check.1019.expected.this.to.be.an.argument.to.the.unary.condition"));
    put("SC1020", message("check.1020.you.need.a.space.before.the.if.single.then.else"));
    put("SC1026", message("check.1026.if.grouping.expressions.inside.use"));
    put("SC1028", message("check.1028.in.you.have.to.escape.or.preferably.combine.expressions"));
    put("SC1029", message("check.1029.in.you.shouldn.t.escape.or"));
    put("SC1035", message("check.1035.you.need.a.space.here"));
    put("SC1036", message("check.1036.is.invalid.here.did.you.forget.to.escape.it"));
    put("SC1037", message("check.1037.braces.are.required.for.positionals.over.9.e.g.10"));
    put("SC1038", message("check.1038.shells.are.space.sensitive.use.cmd.not.cmd"));
    put("SC1039", message("check.1039.remove.indentation.before.end.token.or.use.and.indent.with.tabs"));
    put("SC1040", message("check.1040.when.using.you.can.only.indent.with.tabs"));
    put("SC1041", message("check.1041.found.eof.further.down.but.not.on.a.separate.line"));
    put("SC1042", message("check.1042.found.eof.further.down.but.not.on.a.separate.line"));
    put("SC1044", message("check.1044.couldn.t.find.end.token.eof.in.the.here.document"));
    put("SC1045", message("check.1045.it.s.not.foo.bar.just.foo.bar"));
    put("SC1046", message("check.1046.couldn.t.find.fi.for.this.if"));
    put("SC1047", message("check.1047.expected.fi.matching.previously.mentioned.if"));
    put("SC1048", message("check.1048.can.t.have.empty.then.clauses.use.true.as.a.no.op"));
    put("SC1049", message("check.1049.did.you.forget.the.then.for.this.if"));
    put("SC1050", message("check.1045.expected.then"));
    put("SC1051", message("check.1051.semicolons.directly.after.then.are.not.allowed.just.remove.it"));
    put("SC1052", message("check.1052.semicolons.directly.after.then.are.not.allowed.just.remove.it"));
    put("SC1053", message("check.1053.semicolons.directly.after.else.are.not.allowed.just.remove.it"));
    put("SC1054", message("check.1054.you.need.a.space.after.the"));
    put("SC1058", message("check.1058.expected.do"));
    put("SC1061", message("check.1061.couldn.t.find.done.for.this.do"));
    put("SC1062", message("check.1062.expected.done.matching.previously.mentioned.do"));
    put("SC1064", message("check.1064.expected.a.to.open.the.function.definition"));
    put("SC1065", message("check.1065.trying.to.declare.parameters.don.t.use.and.refer.to.params.as.1.2"));
    put("SC1066", message("check.1066.don.t.use.on.the.left.side.of.assignments"));
    put("SC1068", message("check.1068.don.t.put.spaces.around.the.in.assignments"));
    put("SC1069", message("check.1069.you.need.a.space.before.the"));
    put("SC1071", message("check.1071.shellcheck.only.supports.sh.bash.dash.ksh.scripts.sorry"));
    put("SC1072", message("check.1072.unexpected"));
    put("SC1073", message("check.1073.couldn.t.parse.this.thing.fix.to.allow.more.checks"));
    put("SC1075", message("check.1075.use.elif.instead.of.else.if"));
    put("SC1077", message("check.1077.for.command.expansion.the.tick.should.slant.left.vs"));
    put("SC1078", message("check.1078.did.you.forget.to.close.this.double.quoted.string"));
    put("SC1079", message("check.1079.this.is.actually.an.end.quote.but.due.to.next.char.it.looks.suspect"));
    put("SC1081", message("check.1081.scripts.are.case.sensitive.use.if.not.if"));
    put("SC1082", message("check.1082.this.file.has.a.utf.8.bom.remove.it.with.lc.ctype.c.sed.1s.yourscript"));
    put("SC1083", message("check.1083.this.is.literal.check.expression.missing.or.quote.it"));
    put("SC1084", message("check.1084.use.not.for.the.shebang"));
    put("SC1086", message("check.1086.don.t.use.on.the.iterator.name.in.for.loops"));
    put("SC1087", message("check.1087.use.braces.when.expanding.arrays.e.g.array.idx.or.var.to.quiet"));
    put("SC1088", message("check.1088.parsing.stopped.here.invalid.use.of.parentheses"));
    put("SC1089", message("check.1089.parsing.stopped.here.is.this.keyword.correctly.matched.up"));
    put("SC1090", message("check.1090.can.t.follow.non.constant.source.use.a.directive.to.specify.location"));
    put("SC1091", message("check.1091.not.following.error.message.here"));
    put("SC1094", message("check.1094.parsing.of.sourced.file.failed.ignoring.it"));
    put("SC1095", message("check.1095.you.need.a.space.or.linefeed.between.the.function.name.and.body"));
    put("SC1097", message("check.1097.unexpected.for.assignment.use.for.comparison.use"));
    put("SC1098", message("check.1098.quote.escape.special.characters.when.using.eval.e.g.eval.a.b"));
    put("SC1099", message("check.1099.you.need.a.space.before.the"));
    put("SC1100", message("check.1100.this.is.a.unicode.dash.delete.and.retype.as.ascii.minus"));
    put("SC1101", message("check.1101.delete.trailing.spaces.after.to.break.line.or.use.quotes.for.literal.space"));
    put("SC1102", message("check.1102.shells.disambiguate.differently.or.not.at.all.if.the.first.should.start.command.substitution.add.a.space.after.it"));
    put("SC1104", message("check.1104.use.not.just.for.the.shebang"));
    put("SC1105", message("check.1105.shells.disambiguate.differently.or.not.at.all.if.the.first.should.start.a.subshell.add.a.space.after.it"));
    put("SC1107", message("check.1107.this.directive.is.unknown.it.will.be.ignored"));
    put("SC1108", message("check.1108.you.need.a.space.before.and.after.the"));
    put("SC1109", message("check.1109.this.is.an.unquoted.html.entity.replace.with.corresponding.character"));
    put("SC1110", message("check.1110.this.is.a.unicode.quote.delete.and.retype.it.or.quote.to.make.literal"));
    put("SC1111", message("check.1111.this.is.a.unicode.quote.delete.and.retype.it.or.ignore.singlequote.for.literal"));
    put("SC1112", message("check.1112.this.is.a.unicode.quote.delete.and.retype.it.or.ignore.doublequote.for.literal"));
    put("SC1113", message("check.1113.use.not.just.for.the.sheban"));
    put("SC1114", message("check.1114.remove.leading.spaces.before.the.shebang"));
    put("SC1115", message("check.1115.remove.spaces.between.and.in.the.shebang"));
    put("SC1116", message("check.1116.missing.on.a.expression.or.use.for.arrays"));
    put("SC1117", message("check.1117.backslash.is.literal.in.prefer.explicit.escaping.n"));
    put("SC1118", message("check.1118.delete.whitespace.after.the.here.doc.end.token"));
    put("SC1119", message("check.1119.add.a.linefeed.between.end.token.and.terminating"));
    put("SC1120", message("check.1120.no.comments.allowed.after.here.doc.token.comment.the.next.line.instead"));
    put("SC1121", message("check.1121.add.terminators.and.other.syntax.on.the.line.with.the.not.here"));
    put("SC1122", message("check.1122.nothing.allowed.after.end.token.to.continue.a.command.put.it.on.the.line.with.the"));
    put("SC1123", message("check.1123.shellcheck.directives.are.only.valid.in.front.of.complete.compound.commands.like.if.not.e.g.individual.elif.branches"));
    put("SC1124", message("check.1124.shellcheck.directives.are.only.valid.in.front.of.complete.commands.like.case.statements.not.individual.case.branches"));
    put("SC1126", message("check.1126.place.shellcheck.directives.before.commands.not.after"));
    put("SC1127", message("check.1127.was.this.intended.as.a.comment.use.in.sh"));
    put("SC1128", message("check.1128.the.shebang.must.be.on.the.first.line.delete.blanks.and.move.comments"));
    put("SC1129", message("check.1129.you.need.a.space.before.the"));
    put("SC1130", message("check.1130.you.need.a.space.before.the"));
    put("SC1131", message("check.1131.use.elif.to.start.another.branch"));
    put("SC1132", message("this.terminates.the.command.escape.it.or.add.space.after.to.silence"));
    put("SC1133", message("unexpected.start.of.line.if.breaking.lines.should.be.at.the.end.of.the.previous.one"));
    put("SC2001", message("sc2001.see.if.you.can.use.variable.search.replace.instead"));
    put("SC2002", message("useless.cat.consider.cmd.file.or.cmd.file.instead"));
    put("SC2003", message("expr.is.antiquated.consider.rewriting.this.using.or"));
    put("SC2004", message("is.unnecessary.on.arithmetic.variables"));
    put("SC2005", message("useless.echo.instead.of.echo.cmd.just.use.cmd"));
    put("SC2006", message("use.notation.instead.of.legacy.backticked"));
    put("SC2007", message("use.instead.of.deprecated"));
    put("SC2008", message("echo.doesn.t.read.from.stdin.are.you.sure.you.should.be.piping.to.it"));
    put("SC2009", message("sc2009.consider.using.pgrep.instead.of.grepping.ps.output"));
    put("SC2010", message("don.t.use.ls.grep.use.a.glob.or.a.for.loop.with.a.condition.to.allow.non.alphanumeric.filenames"));
    put("SC2012", message("use.find.instead.of.ls.to.better.handle.non.alphanumeric.filenames"));
    put("SC2013", message("to.read.lines.rather.than.words.pipe.redirect.to.a.while.read.loop"));
    put("SC2014", message("this.will.expand.once.before.find.runs.not.per.file.found"));
    put("SC2015", message("note.that.a.b.c.is.not.if.then.else.c.may.run.when.a.is.true"));
    put("SC2016", message("expressions.don.t.expand.in.single.quotes.use.double.quotes.for.that"));
    put("SC2017", message("increase.precision.by.replacing.a.b.c.with.a.c.b"));
    put("SC2018", message("use.lower.to.support.accents.and.foreign.alphabets"));
    put("SC2019", message("use.upper.to.support.accents.and.foreign.alphabets"));
    put("SC2020", message("tr.replaces.sets.of.chars.not.words.mentioned.due.to.duplicates"));
    put("SC2021", message("don.t.use.around.ranges.in.tr.it.replaces.literal.square.brackets"));
    put("SC2022", message("note.that.unlike.globs.o.here.matches.ooo.but.not.oscar"));
    put("SC2024", message("sudo.doesn.t.affect.redirects.use.sudo.tee.file"));
    put("SC2025", message("make.sure.all.escape.sequences.are.enclosed.in.to.prevent.line.wrapping.issues"));
    put("SC2026", message("this.word.is.outside.of.quotes.did.you.intend.to.nest.single.quotes.instead"));
    put("SC2027", message("the.surrounding.quotes.actually.unquote.this.remove.or.escape.them"));
    put("SC2028", message("echo.won.t.expand.escape.sequences.consider.printf"));
    put("SC2029", message("note.that.unescaped.this.expands.on.the.client.side"));
    put("SC2030", message("modification.of.var.is.local.to.subshell.caused.by.pipeline"));
    put("SC2031", message("var.was.modified.in.a.subshell.that.change.might.be.lost"));
    put("SC2032", message("use.own.script.or.sh.c.to.run.this.from.su"));
    put("SC2033", message("shell.functions.can.t.be.passed.to.external.commands"));
    put("SC2034", message("foo.appears.unused.verify.it.or.export.it"));
    put("SC2035", message("use.glob.or.glob.so.names.with.dashes.won.t.become.options"));
    put("SC2036", message("if.you.wanted.to.assign.the.output.of.the.pipeline.use.a.b.c"));
    put("SC2037", message("to.assign.the.output.of.a.command.use.var.cmd"));
    put("SC2038", message("use.print0.0.or.find.exec.to.allow.for.non.alphanumeric.filenames"));
    put("SC2039", message("in.posix.sh.something.is.undefined"));
    put("SC2040", message("bin.sh.was.specified.so.is.not.supported.even.when.sh.is.actually.bash"));
    put("SC2041", message("this.is.a.literal.string.to.run.as.a.command.use.instead.of"));
    put("SC2043", message("this.loop.will.only.ever.run.once.for.a.constant.value.did.you.perhaps.mean.to.loop.over.dir.var.or.cmd"));
    put("SC2044", message("for.loops.over.find.output.are.fragile.use.find.exec.or.a.while.read.loop"));
    put("SC2045", message("iterating.over.ls.output.is.fragile.use.globs"));
    put("SC2046", message("quote.this.to.prevent.word.splitting"));
    put("SC2048", message("use.with.quotes.to.prevent.whitespace.problems"));
    put("SC2049", message("is.for.regex.but.this.looks.like.a.glob.use.instead"));
    put("SC2050", message("this.expression.is.constant.did.you.forget.the.on.a.variable"));
    put("SC2051", message("bash.doesn.t.support.variables.in.brace.range.expansions"));
    put("SC2053", message("quote.the.rhs.of.in.to.prevent.glob.matching"));
    put("SC2054", message("use.spaces.not.commas.to.separate.array.elements"));
    put("SC2055", message("you.probably.wanted.here"));
    put("SC2056", message("you.probably.wanted.here"));
    put("SC2057", message("unknown.binary.operator"));
    put("SC2058", message("unknown.unaryoperator"));
    put("SC2059", message("don.t.use.variables.in.the.printf.format.string.use.printf.s.foo"));
    put("SC2060", message("quote.parameters.to.tr.to.prevent.glob.expansion"));
    put("SC2061", message("quote.the.parameter.to.name.so.the.shell.won.t.interpret.it"));
    put("SC2062", message("quote.the.grep.pattern.so.the.shell.won.t.interpret.it"));
    put("SC2063", message("grep.uses.regex.but.this.looks.like.a.glob"));
    put("SC2064", message("use.single.quotes.otherwise.this.expands.now.rather.than.when.signalled"));
    put("SC2065", message("this.is.interpreted.as.a.shell.file.redirection.not.a.comparison"));
    put("SC2066", message("since.you.double.quoted.this.it.will.not.word.split.and.the.loop.will.only.run.once"));
    put("SC2067", message("missing.or.terminating.exec.you.can.t.use.and.has.to.be.a.separate.quoted.argument"));
    put("SC2068", message("double.quote.array.expansions.to.avoid.re.splitting.elements"));
    put("SC2069", message("to.redirect.stdout.stderr.2.1.must.be.last.or.use.cmd.file.2.1.to.clarify"));
    put("SC2070", message("n.doesn.t.work.with.unquoted.arguments.quote.or.use"));
    put("SC2071", message("is.for.string.comparisons.use.gt.instead"));
    put("SC2072", message("decimals.are.not.supported.either.use.integers.only.or.use.bc.or.awk.to.compare"));
    put("SC2074", message("can.t.use.in.use.instead"));
    put("SC2076", message("don.t.quote.rhs.of.it.ll.match.literally.rather.than.as.a.regex"));
    put("SC2077", message("you.need.spaces.around.the.comparison.operator"));
    put("SC2078", message("this.expression.is.constant.did.you.forget.a.somewhere"));
    put("SC2079", message("doesn.t.support.decimals.use.bc.or.awk"));
    put("SC2080", message("numbers.with.leading.0.are.considered.octal"));
    put("SC2081", message("can.t.match.globs.use.or.grep"));
    put("SC2082", message("to.expand.via.indirection.use.name.foo.n.echo.name"));
    put("SC2084", message("remove.or.use.expr.to.avoid.executing.output"));
    put("SC2086", message("double.quote.to.prevent.globbing.and.word.splitting"));
    put("SC2087", message("quote.eof.to.make.here.document.expansions.happen.on.the.server.side.rather.than.on.the.client"));
    put("SC2088", message("tilde.does.not.expand.in.quotes.use.home"));
    put("SC2089", message("quotes.backslashes.will.be.treated.literally.use.an.array"));
    put("SC2090", message("quotes.backslashes.in.this.variable.will.not.be.respected"));
    put("SC2091", message("remove.surrounding.to.avoid.executing.output"));
    put("SC2092", message("remove.backticks.to.avoid.executing.output"));
    put("SC2093", message("remove.exec.if.script.should.continue.after.this.command"));
    put("SC2094", message("sc2094.make.sure.not.to.read.and.write.the.same.file.in.the.same.pipeline"));
    put("SC2095", message("add.dev.null.to.prevent.ssh.from.swallowing.stdin"));
    put("SC2096", message("on.most.os.shebangs.can.only.specify.a.single.parameter"));
    put("SC2097", message("this.assignment.is.only.seen.by.the.forked.process"));
    put("SC2098", message("this.expansion.will.not.see.the.mentioned.assignment"));
    put("SC2099", message("use.for.arithmetics.e.g.i.i.2"));
    put("SC2100", message("use.for.arithmetics.e.g.i.i.2"));
    put("SC2101", message("named.class.needs.outer.e.g.digit"));
    put("SC2102", message("ranges.can.only.match.single.chars.mentioned.due.to.duplicates"));
    put("SC2103", message("use.a.subshell.to.avoid.having.to.cd.back"));
    put("SC2104", message("in.functions.use.return.instead.of.break"));
    put("SC2105", message("break.is.only.valid.in.loops"));
    put("SC2106", message("sc2106.this.only.exits.the.subshell.caused.by.the.pipeline"));
    put("SC2107", message("instead.of.a.b.use.a.b2"));
    put("SC2108", message("in.use.instead.of.a"));
    put("SC2109", message("instead.of.a.b.use.a.b"));
    put("SC2110", message("in.use.instead.of.o"));
    put("SC2112", message("function.keyword.is.non.standard.delete.it"));
    put("SC2114", message("warning.deletes.a.system.directory"));
    put("SC2115", message("use.var.to.ensure.this.never.expands.to"));
    put("SC2116", message("useless.echo.instead.of.cmd.echo.foo.just.use.cmd.foo"));
    put("SC2117", message("to.run.commands.as.another.user.use.su.c.or.sudo"));
    put("SC2119", message("use.foo.if.function.s.1.should.mean.script.s.1"));
    put("SC2120", message("foo.references.arguments.but.none.are.ever.passed"));
    put("SC2121", message("to.assign.a.variable.use.just.var.value.no.set"));
    put("SC2122", message("is.not.a.valid.operator.use.a.b.instead"));
    put("SC2123", message("path.is.the.shell.search.path.use.another.name"));
    put("SC2124", message("assigning.an.array.to.a.string.assign.as.array.or.use.instead.of.to.concatenate"));
    put("SC2125", message("brace.expansions.and.globs.are.literal.in.assignments.quote.it.or.use.an.array"));
    put("SC2126", message("consider.using.grep.c.instead.of.grep.wc"));
    put("SC2128", message("expanding.an.array.without.an.index.only.gives.the.first.element"));
    put("SC2129", message("consider.using.cmd1.cmd2.file.instead.of.individual.redirects"));
    put("SC2130", message("eq.is.for.integer.comparisons.use.instead"));
    put("SC2139", message("this.expands.when.defined.not.when.used.consider.escaping"));
    put("SC2140", message("word.is.on.the.form.a.b.c.b.indicated.did.you.mean.abc.or.a.b.c"));
    put("SC2141", message("did.you.mean.ifs"));
    put("SC2142", message("aliases.can.t.use.positional.parameters.use.a.function"));
    put("SC2143", message("use.grep.q.instead.of.comparing.output.with.n"));
    put("SC2144", message("e.doesn.t.work.with.globs.use.a.for.loop"));
    put("SC2145", message("argument.mixes.string.and.array.use.or.separate.argument"));
    put("SC2146", message("this.action.ignores.everything.before.the.o.use.to.group"));
    put("SC2147", message("literal.tilde.in.path.works.poorly.across.programs"));
    put("SC2148", message("tips.depend.on.target.shell.and.yours.is.unknown.add.a.shebang"));
    put("SC2149", message("remove.for.numeric.index.or.escape.it.for.string"));
    put("SC2150", message("exec.does.not.automatically.invoke.a.shell.use.exec.sh.c.for.that"));
    put("SC2151", message("only.one.integer.0.255.can.be.returned.use.stdout.for.other.data"));
    put("SC2152", message("can.only.return.0.255.other.data.should.be.written.to.stdout"));
    put("SC2153", message("possible.misspelling.myvariable.may.not.be.assigned.but.my.variable.is"));
    put("SC2154", message("var.is.referenced.but.not.assigned"));
    put("SC2155", message("declare.and.assign.separately.to.avoid.masking.return.values"));
    put("SC2156", message("injecting.filenames.is.fragile.and.insecure.use.parameters"));
    put("SC2157", message("argument.to.implicit.n.is.always.true.due.to.literal.strings"));
    put("SC2158", message("false.is.true.remove.the.brackets"));
    put("SC2159", message("0.is.true.use.false.instead"));
    put("SC2160", message("instead.of.true.just.use.true"));
    put("SC2161", message("instead.of.1.use.true"));
    put("SC2162", message("read.without.r.will.mangle.backslashes"));
    put("SC2163", message("this.does.not.export.foo.remove.for.that.or.use.var.to.quiet"));
    put("SC2164", message("use.cd.exit.in.case.cd.fails"));
    put("SC2165", message("this.nested.loop.overrides.the.index.variable.of.its.parent"));
    put("SC2166", message("prefer.p.q.as.p.a.q.is.not.well.defined"));
    put("SC2167", message("this.parent.loop.has.its.index.variable.overridden"));
    put("SC2168", message("local.is.only.valid.in.functions"));
    put("SC2169", message("in.dash.something.is.not.supported"));
    put("SC2170", message("numerical.eq.does.not.dereference.in.expand.or.use.string.operator"));
    put("SC2172", message("trapping.signals.by.number.is.not.well.defined.prefer.signal.names"));
    put("SC2173", message("sigkill.sigstop.can.not.be.trapped"));
    put("SC2174", message("when.used.with.p.m.only.applies.to.the.deepest.directory"));
    put("SC2175", message("quote.this.invalid.brace.expansion.since.it.should.be.passed.literally.to.eval"));
    put("SC2176", message("time.is.undefined.for.pipelines.time.single.stage.or.bash.c.instead"));
    put("SC2177", message("time.is.undefined.for.compound.commands.time.sh.c.instead"));
    put("SC2178", message("variable.was.used.as.an.array.but.is.now.assigned.a.string"));
    put("SC2179", message("use.array.item.to.append.items.to.an.array"));
    put("SC2180", message("bash.does.not.support.multidimensional.arrays.use.1d.or.associative.arrays"));
    put("SC2181", message("check.exit.code.directly.with.e.g.if.mycmd.not.indirectly.with"));
    put("SC2182", message("this.printf.format.string.has.no.variables.other.arguments.are.ignored"));
    put("SC2183", message("this.format.string.has.2.variables.but.is.passed.1.arguments"));
    put("SC2184", message("quote.arguments.to.unset.so.they.re.not.glob.expanded"));
    put("SC2185", message("some.finds.don.t.have.a.default.path.specify.explicitly"));
    put("SC2186", message("tempfile.is.deprecated.use.mktemp.instead"));
    put("SC2187", message("ash.scripts.will.be.checked.as.dash.add.shellcheck.shell.dash.to.silence"));
    put("SC2188", message("this.redirection.doesn.t.have.a.command.move.to.its.command.or.use.true.as.no.op"));
    put("SC2189", message("you.can.t.have.between.this.redirection.and.the.command.it.should.apply.to"));
    put("SC2190", message("elements.in.associative.arrays.need.index.e.g.array.index.value"));
    put("SC2191", message("the.here.is.literal.to.assign.by.index.use.index.value.with.no.spaces.to.keep.as.literal.quote.it"));
    put("SC2192", message("this.array.element.has.no.value.remove.spaces.after.or.use.for.empty.string"));
    put("SC2193", message("the.arguments.to.this.comparison.can.never.be.equal.make.sure.your.syntax.is.correct"));
    put("SC2194", message("this.word.is.constant.did.you.forget.the.on.a.variable"));
    put("SC2195", message("this.pattern.will.never.match.the.case.statement.s.word.double.check.them"));
    put("SC2196", message("egrep.is.non.standard.and.deprecated.use.grep.e.instead"));
    put("SC2197", message("fgrep.is.non.standard.and.deprecated.use.grep.f.instead"));
    put("SC2198", message("arrays.don.t.work.as.operands.in.use.a.loop.or.concatenate.with.instead.of"));
    put("SC2199", message("arrays.implicitly.concatenate.in.use.a.loop.or.explicit.instead.of"));
    put("SC2200", message("brace.expansions.don.t.work.as.operands.in.use.a.loop"));
    put("SC2201", message("brace.expansion.doesn.t.happen.in.use.a.loop"));
    put("SC2202", message("globs.don.t.work.as.operands.in.use.a.loop"));
    put("SC2203", message("globs.are.ignored.in.except.right.of.use.a.loop"));
    put("SC2204", message("is.a.subshell.did.you.mean.a.test.expression"));
    put("SC2205", message("is.a.subshell.did.you.mean.a.test.expression"));
    put("SC2206", message("quote.to.prevent.word.splitting.or.split.robustly.with.mapfile.or.read.a"));
    put("SC2207", message("prefer.mapfile.or.read.a.to.split.command.output.or.quote.to.avoid.splitting"));
    put("SC2208", message("use.or.quote.arguments.to.v.to.avoid.glob.expansion"));
    put("SC2209", message("use.var.command.to.assign.output.or.quote.to.assign.string"));
    put("SC2210", message("this.is.a.file.redirection.was.it.supposed.to.be.a.comparison.or.fd.operation"));
    put("SC2211", message("this.is.a.glob.used.as.a.command.name.was.it.supposed.to.be.in.array.or.is.it.missing.quoting"));
    put("SC2212", message("use.false.instead.of.empty.conditionals"));
    put("SC2213", message("getopts.specified.n.but.it.s.not.handled.by.this.case"));
    put("SC2214", message("this.case.is.not.specified.by.getopts"));
    put("SC2215", message("this.flag.is.used.as.a.command.name.bad.line.break.or.missing"));
    put("SC2216", message("piping.to.rm.a.command.that.doesn.t.read.stdin.wrong.command.or.missing.xargs"));
    put("SC2217", message("redirecting.to.echo.a.command.that.doesn.t.read.stdin.bad.quoting.or.missing.xargs"));
    put("SC2218", message("this.function.is.only.defined.later.move.the.definition.up"));
    put("SC2219", message("instead.of.let.expr.prefer.expr"));
    put("SC2220", message("invalid.flags.are.not.handled.add.a.case"));
    put("SC2221", message("this.pattern.always.overrides.a.later.one"));
    put("SC2222", message("this.pattern.never.matches.because.of.a.previous.pattern"));
    put("SC2223", message("this.default.assignment.may.cause.dos.due.to.globbing.quote.it"));
    put("SC2224", message("this.mv.has.no.destination.check.the.arguments"));
    put("SC2225", message("this.cp.has.no.destination.check.the.arguments"));
    put("SC2226", message("this.ln.has.no.destination.check.the.arguments.or.specify.explicitly"));
    put("SC2227", message("redirection.applies.to.the.find.command.itself.rewrite.to.work.per.action.or.move.to.end"));
    put("SC2229", message("this.does.not.read.foo.remove.for.that.or.use.var.to.quiet"));
    put("SC2230", message("which.is.non.standard.use.builtin.command.v.instead"));
    put("SC2231", message("quote.expansions.in.this.for.loop.glob.to.prevent.wordsplitting.e.g.dir.txt"));
    put("SC2232", message("can.t.use.sudo.with.builtins.like.cd.did.you.want.sudo.sh.c.instead"));
    put("SC2233", message("remove.superfluous.around.condition"));
    put("SC2234", message("remove.superfluous.around.test.command"));
    put("SC2235", message("use.instead.of.to.avoid.subshell.overhead"));
    put("SC2236", message("use.n.instead.of.z2"));
    put("SC2237", message("use.n.instead.of.z"));
    put("SC2238", message("redirecting.to.from.command.name.instead.of.file.did.you.want.pipes.xargs.or.quote.to.ignore"));
    put("SC2239", message("ensure.the.shebang.uses.the.absolute.path.to.the.interpreter"));
    put("SC2240", message("the.dot.command.does.not.support.arguments.in.sh.dash.set.them.as.variables"));
    put("SC2241", message("the.exit.status.can.only.be.one.integer.0.255.use.stdout.for.other.data"));
    put("SC2242", message("can.only.exit.with.status.0.255.other.data.should.be.written.to.stdout.stderr"));
    put("SC2243", message("prefer.explicit.n.to.check.for.output.or.run.command.without.to.check.for.success"));
    put("SC2244", message("prefer.explicit.n.to.check.non.empty.string.or.use.ne.to.check.boolean.integer"));
    put("SC2245", message("d.only.applies.to.the.first.expansion.of.this.glob.use.a.loop.to.check.any.all"));
    put("SC2246", message("this.shebang.specifies.a.directory.ensure.the.interpreter.is.a.file"));
    put("SC2247", message("flip.leading.and.if.this.should.be.a.quoted.substitution"));
    put("SC2249", message("consider.adding.a.default.case.even.if.it.just.exits.with.error"));
  }};

  @ApiStatus.Internal
  public static int calcOffset(CharSequence sequence, int startOffset, int column) {
    int i = 1;
    while (i < column) {
      int c = Character.codePointAt(sequence, startOffset);
      i += c == '\t' ? 8 : 1;
      startOffset++;
    }
    return startOffset;
  }
}
