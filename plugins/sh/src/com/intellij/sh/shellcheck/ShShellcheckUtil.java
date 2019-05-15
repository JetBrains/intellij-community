// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.settings.ShSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class ShShellcheckUtil {
  private static final Logger LOG = Logger.getInstance(ShShellcheckUtil.class);
  private static final String SHELLCHECK = "shellcheck";
  private static final String SHELLCHECK_VERSION = "0.6.0-1";
  private static final String SHELLCHECK_ARCHIVE_EXTENSION = ".tar.gz";
  private static final String SHELLCHECK_URL = "https://cache-redirector.jetbrains.com/" +
                                               "jetbrains.bintray.com/intellij-third-party-dependencies/" +
                                               "org/jetbrains/intellij/deps/shellcheck/";
  private static final String DOWNLOAD_PATH = PathManager.getPluginsPath() + File.separator + ShLanguage.INSTANCE.getID();

  static void download(@Nullable Project project, @Nullable Runnable onSuccess) {
    File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    File shellcheck = new File(DOWNLOAD_PATH + File.separator + SHELLCHECK);
    if (shellcheck.exists()) {
      try {
        String path = ShSettings.getShellcheckPath();
        String shellcheckPath = shellcheck.getCanonicalPath();
        if (StringUtil.isNotEmpty(path) && path.equals(shellcheckPath)) {
          LOG.debug("Shellcheck already downloaded");
        }
        else {
          ShSettings.setShellcheckPath(shellcheckPath);
          showInfoNotification();
        }
        if (onSuccess != null) {
          ApplicationManager.getApplication().invokeLater(onSuccess);
        }
        return;
      }
      catch (IOException e) {
        LOG.debug("Can't evaluate shellcheck path", e);
        showErrorNotification();
        return;
      }
    }

    String url = getShellcheckDistributionLink();
    if (StringUtil.isEmpty(url)) {
      LOG.debug("Unsupported OS for shellcheck");
      return;
    }

    String downloadName = SHELLCHECK + SHELLCHECK_ARCHIVE_EXTENSION;
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(url, downloadName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), downloadName);

    Task.Backgroundable task = new Task.Backgroundable(project, "Download Shellcheck") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(new File(DOWNLOAD_PATH));
          Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
          File file = first != null ? first.first : null;
          if (file != null) {
            String path = decompressShellcheck(file.getCanonicalPath(), directory);
            if (StringUtil.isNotEmpty(path)) {
              FileUtilRt.setExecutableAttribute(path, true);
              ShSettings.setShellcheckPath(path);
              showInfoNotification();
              if (onSuccess != null) {
                ApplicationManager.getApplication().invokeLater(onSuccess);
              }
            }
          }
        }
        catch (IOException e) {
          LOG.warn("Can't download shellcheck", e);
          showErrorNotification();
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
  }

  static boolean isValidPath(@Nullable String path) {
    if (path == null) return false;
    if (ShSettings.I_DO_MIND.equals(path)) return true;
    File file = new File(path);
    return file.canExecute() && file.getName().contains("shellcheck");

//    try {
//      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("--version");
//      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);
//
//      return processOutput.getStdout().startsWith("ShellCheck");
//    }
//    catch (ExecutionException e) {
//      LOG.debug("Exception in process execution", e);
//    }
//    return false;
  }

  @NotNull
  private static String decompressShellcheck(@NotNull String tarPath, File directory) throws IOException {
    File archive = new File(tarPath);

    Decompressor.Tar tar = new Decompressor.Tar(archive);
    File tmpDir = new File(directory, "tmp");
    tar.postprocessor(outputFile -> {
      try {
        FileUtil.copyDir(outputFile.getParentFile(), directory);
      }
      catch (IOException e) {
        LOG.warn("Can't decompressor shellcheck", e);
      }
    });
    tar.extract(tmpDir);

    // Cleaning tmp dir and archive
    FileUtil.delete(tmpDir);
    FileUtil.delete(archive);

    File shellcheck = new File(directory, SHELLCHECK + (SystemInfoRt.isWindows ? ".exe" : ""));
    return shellcheck.exists() ? shellcheck.getCanonicalPath() : "";
  }

  @Nullable
  private static String getShellcheckDistributionLink() {
    String platform;
    if (SystemInfoRt.isMac) {
      platform = "mac";
    }
    else if (SystemInfoRt.isLinux) {
      platform = "linux";
    }
    else if (SystemInfoRt.isWindows) {
      platform = "windows";
    }
    else {
      return null;
    }
    return SHELLCHECK_URL + SHELLCHECK_VERSION + "/" + platform + SHELLCHECK_ARCHIVE_EXTENSION;
  }

  private static void showInfoNotification() {
    Notifications.Bus.notify(new Notification("Shell Script", "", "Shellcheck has been successfully installed",
        NotificationType.INFORMATION));
  }

  private static void showErrorNotification() {
    Notifications.Bus.notify(new Notification("Shell Script", "", "Can't download sh shellcheck. Please install it manually",
        NotificationType.ERROR));
  }

  static final Map<String, String> shellCheckCodes = ContainerUtil.stringMap(
    "SC1000", "$ is not used specially and should therefore be escaped.",
    "SC1001", "This \\o will be a regular 'o' in this context.",
    "SC1003", "Want to escape a single quote? echo 'This is how it'\\''s done'.",
    "SC1004", "This backslash+linefeed is literal. Break outside single quotes if you just want to break the line.",
    "SC1007", "Remove space after = if trying to assign a value (or for empty string, use var='' ... ).",
    "SC1008", "This shebang was unrecognized. ShellCheck only supports sh/bash/dash/ksh. Add a 'shell' directive to specify.",
    "SC1009", "The mentioned parser error was in ...",
    "SC1010", "Use semicolon or linefeed before 'done' (or quote to make it literal).",
    "SC1011", "This apostrophe terminated the single quoted string!",
    "SC1012", "`\t` is just literal `t` here. For tab, use \"$(printf '\t')\" instead.",
    "SC1014", "Use 'if cmd; then ..' to check exit code, or 'if [ \"$(cmd)\" = .. ]' to check output.",
    "SC1015", "This is a unicode double quote. Delete and retype it.",
    "SC1016", "This is a Unicode single quote. Delete and retype it.",
    "SC1017", "Literal carriage return. Run script through `tr -d '\r'` .",
    "SC1018", "This is a unicode non-breaking space. Delete it and retype as space.",
    "SC1019", "Expected this to be an argument to the unary condition.",
    "SC1020", "You need a space before the if single then \"]\" else \"]]\"",
    "SC1026", "If grouping expressions inside [[..]], use ( .. ).",
    "SC1028", "In [..] you have to escape \\( \\) or preferably combine [..] expressions.",
    "SC1029", "In `[[..]]` you shouldn't escape `(` or `)`.",
    "SC1035", "You need a space here",
    "SC1036", "`(` is invalid here. Did you forget to escape it?",
    "SC1037", "Braces are required for positionals over 9, e.g. ${10}.",
    "SC1038", "Shells are space sensitive. Use '< <(cmd)', not '<<(cmd)'.",
    "SC1039", "Remove indentation before end token (or use `<<-` and indent with tabs).",
    "SC1040", "When using <<-, you can only indent with tabs.",
    "SC1041", "Found 'eof' further down, but not on a separate line.",
    "SC1042", "Found 'eof' further down, but not on a separate line.",
    "SC1044", "Couldn't find end token `EOF' in the here document.",
    "SC1045", "It's not 'foo &; bar', just 'foo & bar'.",
    "SC1046", "Couldn't find 'fi' for this 'if'.",
    "SC1047", "Expected 'fi' matching previously mentioned 'if'.",
    "SC1048", "Can't have empty then clauses (use 'true' as a no-op).",
    "SC1049", "Did you forget the 'then' for this 'if'?",
    "SC1050", "Expected 'then'.",
    "SC1051", "Semicolons directly after 'then' are not allowed. Just remove it.",
    "SC1052", "Semicolons directly after 'then' are not allowed. Just remove it.",
    "SC1053", "Semicolons directly after 'else' are not allowed. Just remove it.",
    "SC1054", "You need a space after the '{'.",
    "SC1058", "Expected `do`.",
    "SC1061", "Couldn't find 'done' for this 'do'.",
    "SC1062", "Expected 'done' matching previously mentioned 'do'.",
    "SC1064", "Expected a { to open the function definition.",
    "SC1065", "Trying to declare parameters? Don't. Use () and refer to params as $1, $2..",
    "SC1066", "Don't use $ on the left side of assignments.",
    "SC1068", "Don't put spaces around the = in assignments.",
    "SC1069", "You need a space before the [.",
    "SC1071", "ShellCheck only supports sh/bash/dash/ksh scripts. Sorry!",
    "SC1072", "Unexpected ..",
    "SC1073", "Couldn't parse this (thing). Fix to allow more checks.",
    "SC1075", "Use 'elif' instead of 'else if'.",
    "SC1077", "For command expansion, the tick should slant left (` vs ´).",
    "SC1078", "Did you forget to close this double quoted string?",
    "SC1079", "This is actually an end quote, but due to next char it looks suspect.",
    "SC1081", "Scripts are case sensitive. Use 'if', not 'If'.",
    "SC1082", "This file has a UTF-8 BOM. Remove it with: LC_CTYPE=C sed '1s/^...//' < yourscript .",
    "SC1083", "This `{`/`}` is literal. Check expression (missing `;/\n?`) or quote it.",
    "SC1084", "Use #!, not !#, for the shebang.",
    "SC1086", "Don't use $ on the iterator name in for loops.",
    "SC1087", "Use braces when expanding arrays, e.g. ${array[idx]} (or ${var}[.. to quiet).",
    "SC1088", "Parsing stopped here. Invalid use of parentheses?",
    "SC1089", "Parsing stopped here. Is this keyword correctly matched up?",
    "SC1090", "Can't follow non-constant source. Use a directive to specify location.",
    "SC1091", "Not following: (error message here)",
    "SC1094", "Parsing of sourced file failed. Ignoring it.",
    "SC1095", "You need a space or linefeed between the function name and body.",
    "SC1097", "Unexpected ==. For assignment, use =. For comparison, use [/[[.",
    "SC1098", "Quote/escape special characters when using eval, e.g. eval \"a=(b)\".",
    "SC1099", "You need a space before the #.",
    "SC1100", "This is a unicode dash. Delete and retype as ASCII minus.",
    "SC1101", "Delete trailing spaces after \\ to break line (or use quotes for literal space).",
    "SC1102", "Shells disambiguate $(( differently or not at all. If the first $( should start command substitution, add a space after it.",
    "SC1104", "Use #!, not just !, for the shebang.",
    "SC1105", "Shells disambiguate (( differently or not at all. If the first ( should start a subshell, add a space after it.",
    "SC1107", "This directive is unknown. It will be ignored.",
    "SC1108", "You need a space before and after the = .",
    "SC1109", "This is an unquoted HTML entity. Replace with corresponding character.",
    "SC1110", "This is a unicode quote. Delete and retype it (or quote to make literal).",
    "SC1111", "This is a unicode quote. Delete and retype it (or ignore/singlequote for literal).",
    "SC1112", "This is a unicode quote. Delete and retype it (or ignore/doublequote for literal).",
    "SC1113", "Use #!, not just #, for the shebang.",
    "SC1114", "Remove leading spaces before the shebang.",
    "SC1115", "Remove spaces between # and ! in the shebang.",
    "SC1116", "Missing $ on a $((..)) expression? (or use ( ( for arrays).",
    "SC1117", "Backslash is literal in \"\n\". Prefer explicit escaping: \"\\n\".",
    "SC1118", "Delete whitespace after the here-doc end token.",
    "SC1119", "Add a linefeed between end token and terminating ')'.",
    "SC1120", "No comments allowed after here-doc token. Comment the next line instead.",
    "SC1121", "Add ;/& terminators (and other syntax) on the line with the <<, not here.",
    "SC1122", "Nothing allowed after end token. To continue a command, put it on the line with the `<<`.",
    "SC1123", "ShellCheck directives are only valid in front of complete compound commands, like `if`, not e.g. individual `elif` branches.",
    "SC1124", "ShellCheck directives are only valid in front of complete commands like 'case' statements, not individual case branches.",
    "SC1126", "Place shellcheck directives before commands, not after.",
    "SC1127", "Was this intended as a comment? Use `#` in sh.",
    "SC1128", "The shebang must be on the first line. Delete blanks and move comments.",
    "SC1129", "You need a space before the !.",
    "SC1130", "You need a space before the :.",
    "SC1131", "Use `elif` to start another branch.",
    "SC1132", "This `&` terminates the command. Escape it or add space after `&` to silence.",
    "SC1133", "Unexpected start of line. If breaking lines, |/||/&& should be at the end of the previous one.",
    "SC2001", "SC2001: See if you can use ${variable//search/replace} instead.",
    "SC2002", "Useless cat. Consider 'cmd < file | ..' or 'cmd file | ..' instead.",
    "SC2003", "expr is antiquated. Consider rewriting this using $((..)), ${} or \\[\\[ \\]\\].",
    "SC2004", "$/${} is unnecessary on arithmetic variables.",
    "SC2005", "Useless `echo`? Instead of `echo $(cmd)`, just use `cmd`",
    "SC2006", "Use $(...) notation instead of legacy backticked `` `...` ``.",
    "SC2007", "Use $((..)) instead of deprecated $[..]",
    "SC2008", "echo doesn't read from stdin, are you sure you should be piping to it?",
    "SC2009", "SC2009 Consider using pgrep instead of grepping ps output.",
    "SC2010", "Don't use ls | grep. Use a glob or a for loop with a condition to allow non-alphanumeric filenames.",
    "SC2012", "Use `find` instead of `ls` to better handle non-alphanumeric filenames.",
    "SC2013", "To read lines rather than words, pipe/redirect to a 'while read' loop.",
    "SC2014", "This will expand once before find runs, not per file found.",
    "SC2015", "Note that A && B || C is not if-then-else. C may run when A is true.",
    "SC2016", "Expressions don't expand in single quotes, use double quotes for that.",
    "SC2017", "Increase precision by replacing a/b\\*c with a\\*c/b.",
    "SC2018", "Use '[:lower:]' to support accents and foreign alphabets.",
    "SC2019", "Use '[:upper:]' to support accents and foreign alphabets.",
    "SC2020", "tr replaces sets of chars, not words (mentioned due to duplicates).",
    "SC2021", "Don't use [] around ranges in tr, it replaces literal square brackets.",
    "SC2022", "Note that unlike globs, o* here matches 'ooo' but not 'oscar'",
    "SC2024", "`sudo` doesn't affect redirects. Use `..| sudo tee file`",
    "SC2025", "Make sure all escape sequences are enclosed in `\\[..\\]` to prevent line wrapping issues",
    "SC2026", "This word is outside of quotes. Did you intend to 'nest '\"'single quotes'\"' instead'?",
    "SC2027", "The surrounding quotes actually unquote this. Remove or escape them.",
    "SC2028", "echo won't expand escape sequences. Consider printf.",
    "SC2029", "Note that, unescaped, this expands on the client side.",
    "SC2030", "Modification of var is local (to subshell caused by pipeline).",
    "SC2031", "var was modified in a subshell. That change might be lost.",
    "SC2032", "Use own script or sh -c '..' to run this from su.",
    "SC2033", "Shell functions can't be passed to external commands.",
    "SC2034", "foo appears unused. Verify it or export it.",
    "SC2035", "Use ./\\*glob* or -- \\*glob* so names with dashes won't become options.",
    "SC2036", "If you wanted to assign the output of the pipeline, use a=$(b | c) .",
    "SC2037", "To assign the output of a command, use var=$(cmd) .",
    "SC2038", "Use -print0/-0 or find -exec + to allow for non-alphanumeric filenames.",
    "SC2039", "In POSIX sh, *something* is undefined.",
    "SC2040", "#!/bin/sh was specified, so ____ is not supported, even when sh is actually bash.",
    "SC2041", "This is a literal string. To run as a command, use $(..) instead of '..' .",
    "SC2043", "This loop will only ever run once for a constant value. Did you perhaps mean to loop over dir/*, $var or $(cmd)?",
    "SC2044", "For loops over find output are fragile. Use find -exec or a while read loop.",
    "SC2045", "Iterating over ls output is fragile. Use globs.",
    "SC2046", "Quote this to prevent word splitting",
    "SC2048", "Use \"$@\" (with quotes) to prevent whitespace problems.",
    "SC2049", "=~ is for regex, but this looks like a glob. Use = instead.",
    "SC2050", "This expression is constant. Did you forget the `$` on a variable?",
    "SC2051", "Bash doesn't support variables in brace range expansions.",
    "SC2053", "Quote the rhs of = in [[ ]] to prevent glob matching.",
    "SC2054", "Use spaces, not commas, to separate array elements.",
    "SC2055", "You probably wanted && here",
    "SC2056", "You probably wanted && here",
    "SC2057", "Unknown binary operator.",
    "SC2058", "Unknown unaryoperator.",
    "SC2059", "Don't use variables in the printf format string. Use printf \"..%s..\" \"$foo\".",
    "SC2060", "Quote parameters to tr to prevent glob expansion.",
    "SC2061", "Quote the parameter to -name so the shell won't interpret it.",
    "SC2062", "Quote the grep pattern so the shell won't interpret it.",
    "SC2063", "Grep uses regex, but this looks like a glob.",
    "SC2064", "Use single quotes, otherwise this expands now rather than when signalled.",
    "SC2065", "This is interpreted as a shell file redirection, not a comparison.",
    "SC2066", "Since you double quoted this, it will not word split, and the loop will only run once.",
    "SC2067", "Missing ';' or + terminating -exec. You can't use |/||/&&, and ';' has to be a separate, quoted argument.",
    "SC2068", "Double quote array expansions to avoid re-splitting elements.",
    "SC2069", "To redirect stdout+stderr, 2>&1 must be last (or use '{ cmd > file; } 2>&1' to clarify).",
    "SC2070", "`-n` doesn't work with unquoted arguments. Quote or use ``[[ ]]``.",
    "SC2071", "> is for string comparisons. Use -gt instead.",
    "SC2072", "Decimals are not supported. Either use integers only, or use bc or awk to compare.",
    "SC2074", "Can't use `=~` in `[ ]`. Use `[[..]]` instead.",
    "SC2076", "Don't quote rhs of =~, it'll match literally rather than as a regex.",
    "SC2077", "You need spaces around the comparison operator.",
    "SC2078", "This expression is constant. Did you forget a `$` somewhere?",
    "SC2079", "(( )) doesn't support decimals. Use bc or awk.",
    "SC2080", "Numbers with leading 0 are considered octal.",
    "SC2081", "`[ .. ]` can't match globs. Use `[[ .. ]]` or grep.",
    "SC2082", "To expand via indirection, use name=\"foo$n\"; echo \"${!name}\".",
    "SC2084", "Remove '$' or use '_=$((expr))' to avoid executing output.",
    "SC2086", "Double quote to prevent globbing and word splitting.",
    "SC2087", "Quote 'EOF' to make here document expansions happen on the server side rather than on the client.",
    "SC2088", "Tilde does not expand in quotes. Use $HOME.",
    "SC2089", "Quotes/backslashes will be treated literally. Use an array.",
    "SC2090", "Quotes/backslashes in this variable will not be respected.",
    "SC2091", "Remove surrounding $() to avoid executing output.",
    "SC2092", "Remove backticks to avoid executing output.",
    "SC2093", "Remove \"exec \" if script should continue after this command.",
    "SC2094", "SC2094 Make sure not to read and write the same file in the same pipeline.",
    "SC2095", "Add < /dev/null to prevent ssh from swallowing stdin.",
    "SC2096", "On most OS, shebangs can only specify a single parameter.",
    "SC2097", "This assignment is only seen by the forked process.",
    "SC2098", "This expansion will not see the mentioned assignment.",
    "SC2099", "Use `$((..))` for arithmetics, e.g. `i=$((i + 2))`",
    "SC2100", "Use `$((..))` for arithmetics, e.g. `i=$((i + 2))`",
    "SC2101", "Named class needs outer [], e.g. [[:digit:]\\].",
    "SC2102", "Ranges can only match single chars (mentioned due to duplicates).",
    "SC2103", "Use a ( subshell ) to avoid having to cd back.",
    "SC2104", "In functions, use return instead of break.",
    "SC2105", "`break` is only valid in loops",
    "SC2106", "SC2106: This only exits the subshell caused by the pipeline.",
    "SC2107", "Instead of [ a && b ], use [ a ] && [ b ].",
    "SC2108", "In [\\[..]], use && instead of -a.",
    "SC2109", "Instead of [ a || b ], use [ a ] || [ b ].",
    "SC2110", "In [\\[..]], use || instead of -o.",
    "SC2112", "'function' keyword is non-standard. Delete it.",
    "SC2114", "Warning: deletes a system directory.",
    "SC2115", "Use \"${var:?}\" to ensure this never expands to /* .",
    "SC2116", "Useless echo? Instead of 'cmd $(echo foo)', just use 'cmd foo'.",
    "SC2117", "To run commands as another user, use su -c or sudo.",
    "SC2119", "Use foo \"$@\" if function's $1 should mean script's $1.",
    "SC2120", "foo references arguments, but none are ever passed.",
    "SC2121", "To assign a variable, use just 'var=value', no 'set ..'.",
    "SC2122", ">= is not a valid operator. Use '! a < b' instead.",
    "SC2123", "PATH is the shell search path. Use another name.",
    "SC2124", "Assigning an array to a string! Assign as array, or use * instead of @ to concatenate.",
    "SC2125", "Brace expansions and globs are literal in assignments. Quote it or use an array.",
    "SC2126", "Consider using grep -c instead of grep|wc.",
    "SC2128", "Expanding an array without an index only gives the first element.",
    "SC2129", "Consider using { cmd1; cmd2; } >> file instead of individual redirects.",
    "SC2130", "-eq is for integer comparisons. Use = instead.",
    "SC2139", "This expands when defined, not when used. Consider escaping.",
    "SC2140", " Word is on the form \"A\"B\"C\" (B indicated). Did you mean \"ABC\" or \"A\\\"B\\\"C\"?",
    "SC2141", "Did you mean IFS=$'\t' ?",
    "SC2142", "Aliases can't use positional parameters. Use a function.",
    "SC2143", "Use grep -q instead of comparing output with [ -n .. ].",
    "SC2144", "-e doesn't work with globs. Use a for loop.",
    "SC2145", "Argument mixes string and array. Use * or separate argument.",
    "SC2146", "This action ignores everything before the -o. Use \\( \\) to group.",
    "SC2147", "Literal tilde in PATH works poorly across programs.",
    "SC2148", "Tips depend on target shell and yours is unknown. Add a shebang.",
    "SC2149", "Remove $/${} for numeric index, or escape it for string.",
    "SC2150", "-exec does not automatically invoke a shell. Use -exec sh -c .. for that.",
    "SC2151", "Only one integer 0-255 can be returned. Use stdout for other data.",
    "SC2152", "Can only return 0-255. Other data should be written to stdout.",
    "SC2153", "Possible Misspelling: MYVARIABLE may not be assigned, but MY_VARIABLE is.",
    "SC2154", "var is referenced but not assigned.",
    "SC2155", "Declare and assign separately to avoid masking return values.",
    "SC2156", "Injecting filenames is fragile and insecure. Use parameters.",
    "SC2157", "Argument to implicit -n is always true due to literal strings.",
    "SC2158", "[ false ] is true. Remove the brackets",
    "SC2159", "[ 0 ] is true. Use 'false' instead",
    "SC2160", "Instead of '[ true ]', just use 'true'.",
    "SC2161", "Instead of '[ 1 ]', use 'true'.",
    "SC2162", "read without -r will mangle backslashes",
    "SC2163", "This does not export 'FOO'. Remove $/${} for that, or use ${var?} to quiet.",
    "SC2164", "Use cd ... || exit in case cd fails.",
    "SC2165", "This nested loop overrides the index variable of its parent.",
    "SC2166", "Prefer [ p ] && [ q ] as [ p -a q ] is not well defined.",
    "SC2167", "This parent loop has its index variable overridden.",
    "SC2168", "'local' is only valid in functions.",
    "SC2169", "In dash, *something* is not supported.",
    "SC2170", "Numerical -eq does not dereference in [..]. Expand or use string operator.",
    "SC2172", "Trapping signals by number is not well defined. Prefer signal names.",
    "SC2173", "SIGKILL/SIGSTOP can not be trapped.",
    "SC2174", "When used with -p, -m only applies to the deepest directory.",
    "SC2175", "Quote this invalid brace expansion since it should be passed literally to eval",
    "SC2176", "'time' is undefined for pipelines. time single stage or bash -c instead.",
    "SC2177", "'time' is undefined for compound commands, time sh -c instead.",
    "SC2178", "Variable was used as an array but is now assigned a string.",
    "SC2179", "Use array+=(\"item\") to append items to an array.",
    "SC2180", "Bash does not support multidimensional arrays. Use 1D or associative arrays.",
    "SC2181", "Check exit code directly with e.g. 'if mycmd;', not indirectly with $?.",
    "SC2182", "This printf format string has no variables. Other arguments are ignored.",
    "SC2183", "This format string has 2 variables, but is passed 1 arguments.",
    "SC2184", "Quote arguments to unset so they're not glob expanded.",
    "SC2185", "Some finds don't have a default path. Specify '.' explicitly.",
    "SC2186", "tempfile is deprecated. Use mktemp instead.",
    "SC2187", "Ash scripts will be checked as Dash. Add '# shellcheck shell=dash' to silence.",
    "SC2188", "This redirection doesn't have a command. Move to its command (or use 'true' as no-op).",
    "SC2189", "You can't have | between this redirection and the command it should apply to.",
    "SC2190", "Elements in associative arrays need index, e.g. array=( [index]=value ) .",
    "SC2191", "The = here is literal. To assign by index, use ( [index]=value ) with no spaces. To keep as literal, quote it.",
    "SC2192", "This array element has no value. Remove spaces after = or use \"\" for empty string.",
    "SC2193", "The arguments to this comparison can never be equal. Make sure your syntax is correct.",
    "SC2194", "This word is constant. Did you forget the $ on a variable?",
    "SC2195", "This pattern will never match the case statement's word. Double check them.",
    "SC2196", "egrep is non-standard and deprecated. Use grep -E instead.",
    "SC2197", "fgrep is non-standard and deprecated. Use grep -F instead.",
    "SC2198", "Arrays don't work as operands in [ ]. Use a loop (or concatenate with * instead of @).",
    "SC2199", "Arrays implicitly concatenate in `[[ ]]`. Use a loop (or explicit * instead of @).",
    "SC2200", "Brace expansions don't work as operands in [ ]. Use a loop.",
    "SC2201", "Brace expansion doesn't happen in `[[ ]]`. Use a loop.",
    "SC2202", "Globs don't work as operands in [ ]. Use a loop.",
    "SC2203", "Globs are ignored in `[[ ]]` except right of =/!=. Use a loop.",
    "SC2204", "(..) is a subshell. Did you mean [ .. ], a test expression?",
    "SC2205", "(..) is a subshell. Did you mean [ .. ], a test expression?",
    "SC2206", "Quote to prevent word splitting, or split robustly with mapfile or read -a.",
    "SC2207", "Prefer mapfile or read -a to split command output (or quote to avoid splitting).",
    "SC2208", "Use `[[ ]]` or quote arguments to -v to avoid glob expansion.",
    "SC2209", "Use var=$(command) to assign output (or quote to assign string).",
    "SC2210", "This is a file redirection. Was it supposed to be a comparison or fd operation?",
    "SC2211", "This is a glob used as a command name. Was it supposed to be in ${..}, array, or is it missing quoting?",
    "SC2212", "Use 'false' instead of empty [/[[ conditionals.",
    "SC2213", "getopts specified -n, but it's not handled by this 'case'.",
    "SC2214", "This case is not specified by getopts.",
    "SC2215", "This flag is used as a command name. Bad line break or missing `[ .. ]`?",
    "SC2216", "Piping to 'rm', a command that doesn't read stdin. Wrong command or missing xargs?",
    "SC2217", "Redirecting to 'echo', a command that doesn't read stdin. Bad quoting or missing xargs?",
    "SC2218", "This function is only defined later. Move the definition up.",
    "SC2219", "Instead of `let expr`, prefer `(( expr ))` .",
    "SC2220", "Invalid flags are not handled. Add a `*)` case.",
    "SC2221", "This pattern always overrides a later one.",
    "SC2222", "This pattern never matches because of a previous pattern.",
    "SC2223", "This default assignment may cause DoS due to globbing. Quote it.",
    "SC2224", "This mv has no destination. Check the arguments.",
    "SC2225", "This cp has no destination. Check the arguments.",
    "SC2226", "This ln has no destination. Check the arguments, or specify '.' explicitly.",
    "SC2227", "Redirection applies to the find command itself. Rewrite to work per action (or move to end).",
    "SC2229", "This does not read 'foo'. Remove $/${} for that, or use ${var?} to quiet.",
    "SC2230", "which is non-standard. Use builtin 'command -v' instead.",
    "SC2231", "Quote expansions in this for loop glob to prevent wordsplitting, e.g. \"$dir\"/*.txt .",
    "SC2232", "Can't use sudo with builtins like cd. Did you want sudo sh -c .. instead?",
    "SC2233", "Remove superfluous `(..)` around condition.",
    "SC2234", "Remove superfluous `(..)` around test command.",
    "SC2235", "Use `{ ..; }` instead of `(..)` to avoid subshell overhead.",
    "SC2236", "Use `-n` instead of `! -z`.",
    "SC2237", "Use `[ -n .. ]` instead of `! [ -z .. ]`.",
    "SC2238", "Redirecting to/from command name instead of file. Did you want pipes/xargs (or quote to ignore)?",
    "SC2239", "Ensure the shebang uses the absolute path to the interpreter.",
    "SC2240", "The dot command does not support arguments in sh/dash. Set them as variables.",
    "SC2241", "The exit status can only be one integer 0-255. Use stdout for other data.",
    "SC2242", "Can only exit with status 0-255. Other data should be written to stdout/stderr.",
    "SC2243", "Prefer explicit -n to check for output (or run command without [/[[ to check for success)",
    "SC2244", "Prefer explicit -n to check non-empty string (or use =/-ne to check boolean/integer).",
    "SC2245", "-d only applies to the first expansion of this glob. Use a loop to check any/all.",
    "SC2246", "This shebang specifies a directory. Ensure the interpreter is a file.",
    "SC2247", "Flip leading $ and \" if this should be a quoted substitution.",
    "SC2249", "Consider adding a default *) case, even if it just exits with error.");
}
