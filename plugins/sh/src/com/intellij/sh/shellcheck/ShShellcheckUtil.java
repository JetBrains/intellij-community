// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
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
import java.util.TreeMap;

class ShShellcheckUtil {
  private static final Logger LOG = Logger.getInstance(ShShellcheckUtil.class);
  private static final String FEATURE_ACTION_ID = "ExternalAnnotatorDownloaded";
  private static final String WINDOWS_EXTENSION = ".exe";
  static final String SHELLCHECK = "shellcheck";
  static final String SHELLCHECK_VERSION = "0.6.0-1";
  static final String SHELLCHECK_ARCHIVE_EXTENSION = ".tar.gz";
  static final String SHELLCHECK_URL = "https://jetbrains.bintray.com/" +
                                       "intellij-third-party-dependencies/" +
                                       "org/jetbrains/intellij/deps/shellcheck/";
  private static final String DOWNLOAD_PATH = PathManager.getPluginsPath() + File.separator + ShLanguage.INSTANCE.getID();

  static void download(@Nullable Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    File shellcheck = new File(DOWNLOAD_PATH + File.separator + SHELLCHECK + (SystemInfo.isWindows ? WINDOWS_EXTENSION : ""));
    if (shellcheck.exists()) {
      try {
        String path = ShSettings.getShellcheckPath();
        String shellcheckPath = shellcheck.getCanonicalPath();
        if (StringUtil.isNotEmpty(path) && path.equals(shellcheckPath)) {
          LOG.debug("Shellcheck already downloaded");
        }
        else {
          ShSettings.setShellcheckPath(shellcheckPath);
        }
        ApplicationManager.getApplication().invokeLater(onSuccess);
        return;
      }
      catch (IOException e) {
        LOG.debug("Can't evaluate shellcheck path", e);
        ApplicationManager.getApplication().invokeLater(onFailure);
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
              FileUtil.setExecutable(new File(path));
              ShSettings.setShellcheckPath(path);
              ApplicationManager.getApplication().invokeLater(onSuccess);
              ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
            }
          }
        }
        catch (IOException e) {
          LOG.warn("Can't download shellcheck", e);
          ApplicationManager.getApplication().invokeLater(onFailure);
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  static boolean isExecutionValidPath(@Nullable String path) {
    if (path == null || ShSettings.I_DO_MIND.equals(path)) return false;
    File file = new File(path);
    return file.canExecute() && file.getName().contains(SHELLCHECK);
  }

  static boolean isValidPath(@Nullable String path) {
    if (path == null) return false;
    if (ShSettings.I_DO_MIND.equals(path)) return true;
    File file = new File(path);
    return file.canExecute() && file.getName().contains(SHELLCHECK);

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
  static String decompressShellcheck(@NotNull String tarPath, File directory) throws IOException {
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

    File shellcheck = new File(directory, SHELLCHECK + (SystemInfo.isWindows ? WINDOWS_EXTENSION : ""));
    return shellcheck.exists() ? shellcheck.getCanonicalPath() : "";
  }

  @Nullable
  private static String getShellcheckDistributionLink() {
    String platform = getPlatform();
    if (platform == null) return null;
    return SHELLCHECK_URL + SHELLCHECK_VERSION + "/" + platform + SHELLCHECK_ARCHIVE_EXTENSION;
  }

  @Nullable
  private static String getPlatform() {
    if (SystemInfo.isMac) return "mac";
    if (SystemInfo.isLinux) return "linux";
    if (SystemInfo.isWindows) return "windows";
    return null;
  }

  static final Map<String, String> shellCheckCodes = new TreeMap<String, String>(){{
    put("SC1000", "$ is not used specially and should therefore be escaped.");
    put("SC1001", "This \\o will be a regular 'o' in this context.");
    put("SC1003", "Want to escape a single quote? echo 'This is how it'\\''s done'.");
    put("SC1004", "This backslash+linefeed is literal. Break outside single quotes if you just want to break the line.");
    put("SC1007", "Remove space after = if trying to assign a value (or for empty string, use var='' ... ).");
    put("SC1008", "This shebang was unrecognized. ShellCheck only supports sh/bash/dash/ksh. Add a 'shell' directive to specify.");
    put("SC1009", "The mentioned parser error was in ...");
    put("SC1010", "Use semicolon or linefeed before 'done' (or quote to make it literal).");
    put("SC1011", "This apostrophe terminated the single quoted string!");
    put("SC1012", "`\t` is just literal `t` here. For tab, use \"$(printf '\t')\" instead.");
    put("SC1014", "Use 'if cmd; then ..' to check exit code, or 'if [ \"$(cmd)\" = .. ]' to check output.");
    put("SC1015", "This is a unicode double quote. Delete and retype it.");
    put("SC1016", "This is a Unicode single quote. Delete and retype it.");
    put("SC1017", "Literal carriage return. Run script through `tr -d '\r'` .");
    put("SC1018", "This is a unicode non-breaking space. Delete it and retype as space.");
    put("SC1019", "Expected this to be an argument to the unary condition.");
    put("SC1020", "You need a space before the if single then \"]\" else \"]]\"");
    put("SC1026", "If grouping expressions inside [[..]], use ( .. ).");
    put("SC1028", "In [..] you have to escape \\( \\) or preferably combine [..] expressions.");
    put("SC1029", "In `[[..]]` you shouldn't escape `(` or `)`.");
    put("SC1035", "You need a space here");
    put("SC1036", "`(` is invalid here. Did you forget to escape it?");
    put("SC1037", "Braces are required for positionals over 9, e.g. ${10}.");
    put("SC1038", "Shells are space sensitive. Use '< <(cmd)', not '<<(cmd)'.");
    put("SC1039", "Remove indentation before end token (or use `<<-` and indent with tabs).");
    put("SC1040", "When using <<-, you can only indent with tabs.");
    put("SC1041", "Found 'eof' further down, but not on a separate line.");
    put("SC1042", "Found 'eof' further down, but not on a separate line.");
    put("SC1044", "Couldn't find end token `EOF' in the here document.");
    put("SC1045", "It's not 'foo &; bar', just 'foo & bar'.");
    put("SC1046", "Couldn't find 'fi' for this 'if'.");
    put("SC1047", "Expected 'fi' matching previously mentioned 'if'.");
    put("SC1048", "Can't have empty then clauses (use 'true' as a no-op).");
    put("SC1049", "Did you forget the 'then' for this 'if'?");
    put("SC1050", "Expected 'then'.");
    put("SC1051", "Semicolons directly after 'then' are not allowed. Just remove it.");
    put("SC1052", "Semicolons directly after 'then' are not allowed. Just remove it.");
    put("SC1053", "Semicolons directly after 'else' are not allowed. Just remove it.");
    put("SC1054", "You need a space after the '{'.");
    put("SC1058", "Expected `do`.");
    put("SC1061", "Couldn't find 'done' for this 'do'.");
    put("SC1062", "Expected 'done' matching previously mentioned 'do'.");
    put("SC1064", "Expected a { to open the function definition.");
    put("SC1065", "Trying to declare parameters? Don't. Use () and refer to params as $1, $2..");
    put("SC1066", "Don't use $ on the left side of assignments.");
    put("SC1068", "Don't put spaces around the = in assignments.");
    put("SC1069", "You need a space before the [.");
    put("SC1071", "ShellCheck only supports sh/bash/dash/ksh scripts. Sorry!");
    put("SC1072", "Unexpected ..");
    put("SC1073", "Couldn't parse this (thing). Fix to allow more checks.");
    put("SC1075", "Use 'elif' instead of 'else if'.");
    put("SC1077", "For command expansion, the tick should slant left (` vs ´).");
    put("SC1078", "Did you forget to close this double quoted string?");
    put("SC1079", "This is actually an end quote, but due to next char it looks suspect.");
    put("SC1081", "Scripts are case sensitive. Use 'if', not 'If'.");
    put("SC1082", "This file has a UTF-8 BOM. Remove it with: LC_CTYPE=C sed '1s/^...//' < yourscript .");
    put("SC1083", "This `{`/`}` is literal. Check expression (missing `;/\n?`) or quote it.");
    put("SC1084", "Use #!, not !#, for the shebang.");
    put("SC1086", "Don't use $ on the iterator name in for loops.");
    put("SC1087", "Use braces when expanding arrays, e.g. ${array[idx]} (or ${var}[.. to quiet).");
    put("SC1088", "Parsing stopped here. Invalid use of parentheses?");
    put("SC1089", "Parsing stopped here. Is this keyword correctly matched up?");
    put("SC1090", "Can't follow non-constant source. Use a directive to specify location.");
    put("SC1091", "Not following: (error message here)");
    put("SC1094", "Parsing of sourced file failed. Ignoring it.");
    put("SC1095", "You need a space or linefeed between the function name and body.");
    put("SC1097", "Unexpected ==. For assignment, use =. For comparison, use [/[[.");
    put("SC1098", "Quote/escape special characters when using eval, e.g. eval \"a=(b)\".");
    put("SC1099", "You need a space before the #.");
    put("SC1100", "This is a unicode dash. Delete and retype as ASCII minus.");
    put("SC1101", "Delete trailing spaces after \\ to break line (or use quotes for literal space).");
    put("SC1102", "Shells disambiguate $(( differently or not at all. If the first $( should start command substitution, add a space after it.");
    put("SC1104", "Use #!, not just !, for the shebang.");
    put("SC1105", "Shells disambiguate (( differently or not at all. If the first ( should start a subshell, add a space after it.");
    put("SC1107", "This directive is unknown. It will be ignored.");
    put("SC1108", "You need a space before and after the = .");
    put("SC1109", "This is an unquoted HTML entity. Replace with corresponding character.");
    put("SC1110", "This is a unicode quote. Delete and retype it (or quote to make literal).");
    put("SC1111", "This is a unicode quote. Delete and retype it (or ignore/singlequote for literal).");
    put("SC1112", "This is a unicode quote. Delete and retype it (or ignore/doublequote for literal).");
    put("SC1113", "Use #!, not just #, for the shebang.");
    put("SC1114", "Remove leading spaces before the shebang.");
    put("SC1115", "Remove spaces between # and ! in the shebang.");
    put("SC1116", "Missing $ on a $((..)) expression? (or use ( ( for arrays).");
    put("SC1117", "Backslash is literal in \"\n\". Prefer explicit escaping: \"\\n\".");
    put("SC1118", "Delete whitespace after the here-doc end token.");
    put("SC1119", "Add a linefeed between end token and terminating ')'.");
    put("SC1120", "No comments allowed after here-doc token. Comment the next line instead.");
    put("SC1121", "Add ;/& terminators (and other syntax) on the line with the <<, not here.");
    put("SC1122", "Nothing allowed after end token. To continue a command, put it on the line with the `<<`.");
    put("SC1123", "ShellCheck directives are only valid in front of complete compound commands, like `if`, not e.g. individual `elif` branches.");
    put("SC1124", "ShellCheck directives are only valid in front of complete commands like 'case' statements, not individual case branches.");
    put("SC1126", "Place shellcheck directives before commands, not after.");
    put("SC1127", "Was this intended as a comment? Use `#` in sh.");
    put("SC1128", "The shebang must be on the first line. Delete blanks and move comments.");
    put("SC1129", "You need a space before the !.");
    put("SC1130", "You need a space before the :.");
    put("SC1131", "Use `elif` to start another branch.");
    put("SC1132", "This `&` terminates the command. Escape it or add space after `&` to silence.");
    put("SC1133", "Unexpected start of line. If breaking lines, |/||/&& should be at the end of the previous one.");
    put("SC2001", "SC2001: See if you can use ${variable//search/replace} instead.");
    put("SC2002", "Useless cat. Consider 'cmd < file | ..' or 'cmd file | ..' instead.");
    put("SC2003", "expr is antiquated. Consider rewriting this using $((..)), ${} or \\[\\[ \\]\\].");
    put("SC2004", "$/${} is unnecessary on arithmetic variables.");
    put("SC2005", "Useless `echo`? Instead of `echo $(cmd)`, just use `cmd`");
    put("SC2006", "Use $(...) notation instead of legacy backticked `` `...` ``.");
    put("SC2007", "Use $((..)) instead of deprecated $[..]");
    put("SC2008", "echo doesn't read from stdin, are you sure you should be piping to it?");
    put("SC2009", "SC2009 Consider using pgrep instead of grepping ps output.");
    put("SC2010", "Don't use ls | grep. Use a glob or a for loop with a condition to allow non-alphanumeric filenames.");
    put("SC2012", "Use `find` instead of `ls` to better handle non-alphanumeric filenames.");
    put("SC2013", "To read lines rather than words, pipe/redirect to a 'while read' loop.");
    put("SC2014", "This will expand once before find runs, not per file found.");
    put("SC2015", "Note that A && B || C is not if-then-else. C may run when A is true.");
    put("SC2016", "Expressions don't expand in single quotes, use double quotes for that.");
    put("SC2017", "Increase precision by replacing a/b\\*c with a\\*c/b.");
    put("SC2018", "Use '[:lower:]' to support accents and foreign alphabets.");
    put("SC2019", "Use '[:upper:]' to support accents and foreign alphabets.");
    put("SC2020", "tr replaces sets of chars, not words (mentioned due to duplicates).");
    put("SC2021", "Don't use [] around ranges in tr, it replaces literal square brackets.");
    put("SC2022", "Note that unlike globs, o* here matches 'ooo' but not 'oscar'");
    put("SC2024", "`sudo` doesn't affect redirects. Use `..| sudo tee file`");
    put("SC2025", "Make sure all escape sequences are enclosed in `\\[..\\]` to prevent line wrapping issues");
    put("SC2026", "This word is outside of quotes. Did you intend to 'nest '\"'single quotes'\"' instead'?");
    put("SC2027", "The surrounding quotes actually unquote this. Remove or escape them.");
    put("SC2028", "echo won't expand escape sequences. Consider printf.");
    put("SC2029", "Note that, unescaped, this expands on the client side.");
    put("SC2030", "Modification of var is local (to subshell caused by pipeline).");
    put("SC2031", "var was modified in a subshell. That change might be lost.");
    put("SC2032", "Use own script or sh -c '..' to run this from su.");
    put("SC2033", "Shell functions can't be passed to external commands.");
    put("SC2034", "foo appears unused. Verify it or export it.");
    put("SC2035", "Use ./\\*glob* or -- \\*glob* so names with dashes won't become options.");
    put("SC2036", "If you wanted to assign the output of the pipeline, use a=$(b | c) .");
    put("SC2037", "To assign the output of a command, use var=$(cmd) .");
    put("SC2038", "Use -print0/-0 or find -exec + to allow for non-alphanumeric filenames.");
    put("SC2039", "In POSIX sh, *something* is undefined.");
    put("SC2040", "#!/bin/sh was specified, so ____ is not supported, even when sh is actually bash.");
    put("SC2041", "This is a literal string. To run as a command, use $(..) instead of '..' .");
    put("SC2043", "This loop will only ever run once for a constant value. Did you perhaps mean to loop over dir/*, $var or $(cmd)?");
    put("SC2044", "For loops over find output are fragile. Use find -exec or a while read loop.");
    put("SC2045", "Iterating over ls output is fragile. Use globs.");
    put("SC2046", "Quote this to prevent word splitting");
    put("SC2048", "Use \"$@\" (with quotes) to prevent whitespace problems.");
    put("SC2049", "=~ is for regex, but this looks like a glob. Use = instead.");
    put("SC2050", "This expression is constant. Did you forget the `$` on a variable?");
    put("SC2051", "Bash doesn't support variables in brace range expansions.");
    put("SC2053", "Quote the rhs of = in [[ ]] to prevent glob matching.");
    put("SC2054", "Use spaces, not commas, to separate array elements.");
    put("SC2055", "You probably wanted && here");
    put("SC2056", "You probably wanted && here");
    put("SC2057", "Unknown binary operator.");
    put("SC2058", "Unknown unaryoperator.");
    put("SC2059", "Don't use variables in the printf format string. Use printf \"..%s..\" \"$foo\".");
    put("SC2060", "Quote parameters to tr to prevent glob expansion.");
    put("SC2061", "Quote the parameter to -name so the shell won't interpret it.");
    put("SC2062", "Quote the grep pattern so the shell won't interpret it.");
    put("SC2063", "Grep uses regex, but this looks like a glob.");
    put("SC2064", "Use single quotes, otherwise this expands now rather than when signalled.");
    put("SC2065", "This is interpreted as a shell file redirection, not a comparison.");
    put("SC2066", "Since you double quoted this, it will not word split, and the loop will only run once.");
    put("SC2067", "Missing ';' or + terminating -exec. You can't use |/||/&&, and ';' has to be a separate, quoted argument.");
    put("SC2068", "Double quote array expansions to avoid re-splitting elements.");
    put("SC2069", "To redirect stdout+stderr, 2>&1 must be last (or use '{ cmd > file; } 2>&1' to clarify).");
    put("SC2070", "`-n` doesn't work with unquoted arguments. Quote or use ``[[ ]]``.");
    put("SC2071", "> is for string comparisons. Use -gt instead.");
    put("SC2072", "Decimals are not supported. Either use integers only, or use bc or awk to compare.");
    put("SC2074", "Can't use `=~` in `[ ]`. Use `[[..]]` instead.");
    put("SC2076", "Don't quote rhs of =~, it'll match literally rather than as a regex.");
    put("SC2077", "You need spaces around the comparison operator.");
    put("SC2078", "This expression is constant. Did you forget a `$` somewhere?");
    put("SC2079", "(( )) doesn't support decimals. Use bc or awk.");
    put("SC2080", "Numbers with leading 0 are considered octal.");
    put("SC2081", "`[ .. ]` can't match globs. Use `[[ .. ]]` or grep.");
    put("SC2082", "To expand via indirection, use name=\"foo$n\"; echo \"${!name}\".");
    put("SC2084", "Remove '$' or use '_=$((expr))' to avoid executing output.");
    put("SC2086", "Double quote to prevent globbing and word splitting.");
    put("SC2087", "Quote 'EOF' to make here document expansions happen on the server side rather than on the client.");
    put("SC2088", "Tilde does not expand in quotes. Use $HOME.");
    put("SC2089", "Quotes/backslashes will be treated literally. Use an array.");
    put("SC2090", "Quotes/backslashes in this variable will not be respected.");
    put("SC2091", "Remove surrounding $() to avoid executing output.");
    put("SC2092", "Remove backticks to avoid executing output.");
    put("SC2093", "Remove \"exec \" if script should continue after this command.");
    put("SC2094", "SC2094 Make sure not to read and write the same file in the same pipeline.");
    put("SC2095", "Add < /dev/null to prevent ssh from swallowing stdin.");
    put("SC2096", "On most OS, shebangs can only specify a single parameter.");
    put("SC2097", "This assignment is only seen by the forked process.");
    put("SC2098", "This expansion will not see the mentioned assignment.");
    put("SC2099", "Use `$((..))` for arithmetics, e.g. `i=$((i + 2))`");
    put("SC2100", "Use `$((..))` for arithmetics, e.g. `i=$((i + 2))`");
    put("SC2101", "Named class needs outer [], e.g. [[:digit:]\\].");
    put("SC2102", "Ranges can only match single chars (mentioned due to duplicates).");
    put("SC2103", "Use a ( subshell ) to avoid having to cd back.");
    put("SC2104", "In functions, use return instead of break.");
    put("SC2105", "`break` is only valid in loops");
    put("SC2106", "SC2106: This only exits the subshell caused by the pipeline.");
    put("SC2107", "Instead of [ a && b ], use [ a ] && [ b ].");
    put("SC2108", "In [\\[..]], use && instead of -a.");
    put("SC2109", "Instead of [ a || b ], use [ a ] || [ b ].");
    put("SC2110", "In [\\[..]], use || instead of -o.");
    put("SC2112", "'function' keyword is non-standard. Delete it.");
    put("SC2114", "Warning: deletes a system directory.");
    put("SC2115", "Use \"${var:?}\" to ensure this never expands to /* .");
    put("SC2116", "Useless echo? Instead of 'cmd $(echo foo)', just use 'cmd foo'.");
    put("SC2117", "To run commands as another user, use su -c or sudo.");
    put("SC2119", "Use foo \"$@\" if function's $1 should mean script's $1.");
    put("SC2120", "foo references arguments, but none are ever passed.");
    put("SC2121", "To assign a variable, use just 'var=value', no 'set ..'.");
    put("SC2122", ">= is not a valid operator. Use '! a < b' instead.");
    put("SC2123", "PATH is the shell search path. Use another name.");
    put("SC2124", "Assigning an array to a string! Assign as array, or use * instead of @ to concatenate.");
    put("SC2125", "Brace expansions and globs are literal in assignments. Quote it or use an array.");
    put("SC2126", "Consider using grep -c instead of grep|wc.");
    put("SC2128", "Expanding an array without an index only gives the first element.");
    put("SC2129", "Consider using { cmd1; cmd2; } >> file instead of individual redirects.");
    put("SC2130", "-eq is for integer comparisons. Use = instead.");
    put("SC2139", "This expands when defined, not when used. Consider escaping.");
    put("SC2140", " Word is on the form \"A\"B\"C\" (B indicated). Did you mean \"ABC\" or \"A\\\"B\\\"C\"?");
    put("SC2141", "Did you mean IFS=$'\t' ?");
    put("SC2142", "Aliases can't use positional parameters. Use a function.");
    put("SC2143", "Use grep -q instead of comparing output with [ -n .. ].");
    put("SC2144", "-e doesn't work with globs. Use a for loop.");
    put("SC2145", "Argument mixes string and array. Use * or separate argument.");
    put("SC2146", "This action ignores everything before the -o. Use \\( \\) to group.");
    put("SC2147", "Literal tilde in PATH works poorly across programs.");
    put("SC2148", "Tips depend on target shell and yours is unknown. Add a shebang.");
    put("SC2149", "Remove $/${} for numeric index, or escape it for string.");
    put("SC2150", "-exec does not automatically invoke a shell. Use -exec sh -c .. for that.");
    put("SC2151", "Only one integer 0-255 can be returned. Use stdout for other data.");
    put("SC2152", "Can only return 0-255. Other data should be written to stdout.");
    put("SC2153", "Possible Misspelling: MYVARIABLE may not be assigned, but MY_VARIABLE is.");
    put("SC2154", "var is referenced but not assigned.");
    put("SC2155", "Declare and assign separately to avoid masking return values.");
    put("SC2156", "Injecting filenames is fragile and insecure. Use parameters.");
    put("SC2157", "Argument to implicit -n is always true due to literal strings.");
    put("SC2158", "[ false ] is true. Remove the brackets");
    put("SC2159", "[ 0 ] is true. Use 'false' instead");
    put("SC2160", "Instead of '[ true ]', just use 'true'.");
    put("SC2161", "Instead of '[ 1 ]', use 'true'.");
    put("SC2162", "read without -r will mangle backslashes");
    put("SC2163", "This does not export 'FOO'. Remove $/${} for that, or use ${var?} to quiet.");
    put("SC2164", "Use cd ... || exit in case cd fails.");
    put("SC2165", "This nested loop overrides the index variable of its parent.");
    put("SC2166", "Prefer [ p ] && [ q ] as [ p -a q ] is not well defined.");
    put("SC2167", "This parent loop has its index variable overridden.");
    put("SC2168", "'local' is only valid in functions.");
    put("SC2169", "In dash, *something* is not supported.");
    put("SC2170", "Numerical -eq does not dereference in [..]. Expand or use string operator.");
    put("SC2172", "Trapping signals by number is not well defined. Prefer signal names.");
    put("SC2173", "SIGKILL/SIGSTOP can not be trapped.");
    put("SC2174", "When used with -p, -m only applies to the deepest directory.");
    put("SC2175", "Quote this invalid brace expansion since it should be passed literally to eval");
    put("SC2176", "'time' is undefined for pipelines. time single stage or bash -c instead.");
    put("SC2177", "'time' is undefined for compound commands, time sh -c instead.");
    put("SC2178", "Variable was used as an array but is now assigned a string.");
    put("SC2179", "Use array+=(\"item\") to append items to an array.");
    put("SC2180", "Bash does not support multidimensional arrays. Use 1D or associative arrays.");
    put("SC2181", "Check exit code directly with e.g. 'if mycmd;', not indirectly with $?.");
    put("SC2182", "This printf format string has no variables. Other arguments are ignored.");
    put("SC2183", "This format string has 2 variables, but is passed 1 arguments.");
    put("SC2184", "Quote arguments to unset so they're not glob expanded.");
    put("SC2185", "Some finds don't have a default path. Specify '.' explicitly.");
    put("SC2186", "tempfile is deprecated. Use mktemp instead.");
    put("SC2187", "Ash scripts will be checked as Dash. Add '# shellcheck shell=dash' to silence.");
    put("SC2188", "This redirection doesn't have a command. Move to its command (or use 'true' as no-op).");
    put("SC2189", "You can't have | between this redirection and the command it should apply to.");
    put("SC2190", "Elements in associative arrays need index, e.g. array=( [index]=value ) .");
    put("SC2191", "The = here is literal. To assign by index, use ( [index]=value ) with no spaces. To keep as literal, quote it.");
    put("SC2192", "This array element has no value. Remove spaces after = or use \"\" for empty string.");
    put("SC2193", "The arguments to this comparison can never be equal. Make sure your syntax is correct.");
    put("SC2194", "This word is constant. Did you forget the $ on a variable?");
    put("SC2195", "This pattern will never match the case statement's word. Double check them.");
    put("SC2196", "egrep is non-standard and deprecated. Use grep -E instead.");
    put("SC2197", "fgrep is non-standard and deprecated. Use grep -F instead.");
    put("SC2198", "Arrays don't work as operands in [ ]. Use a loop (or concatenate with * instead of @).");
    put("SC2199", "Arrays implicitly concatenate in `[[ ]]`. Use a loop (or explicit * instead of @).");
    put("SC2200", "Brace expansions don't work as operands in [ ]. Use a loop.");
    put("SC2201", "Brace expansion doesn't happen in `[[ ]]`. Use a loop.");
    put("SC2202", "Globs don't work as operands in [ ]. Use a loop.");
    put("SC2203", "Globs are ignored in `[[ ]]` except right of =/!=. Use a loop.");
    put("SC2204", "(..) is a subshell. Did you mean [ .. ], a test expression?");
    put("SC2205", "(..) is a subshell. Did you mean [ .. ], a test expression?");
    put("SC2206", "Quote to prevent word splitting, or split robustly with mapfile or read -a.");
    put("SC2207", "Prefer mapfile or read -a to split command output (or quote to avoid splitting).");
    put("SC2208", "Use `[[ ]]` or quote arguments to -v to avoid glob expansion.");
    put("SC2209", "Use var=$(command) to assign output (or quote to assign string).");
    put("SC2210", "This is a file redirection. Was it supposed to be a comparison or fd operation?");
    put("SC2211", "This is a glob used as a command name. Was it supposed to be in ${..}, array, or is it missing quoting?");
    put("SC2212", "Use 'false' instead of empty [/[[ conditionals.");
    put("SC2213", "getopts specified -n, but it's not handled by this 'case'.");
    put("SC2214", "This case is not specified by getopts.");
    put("SC2215", "This flag is used as a command name. Bad line break or missing `[ .. ]`?");
    put("SC2216", "Piping to 'rm', a command that doesn't read stdin. Wrong command or missing xargs?");
    put("SC2217", "Redirecting to 'echo', a command that doesn't read stdin. Bad quoting or missing xargs?");
    put("SC2218", "This function is only defined later. Move the definition up.");
    put("SC2219", "Instead of `let expr`, prefer `(( expr ))` .");
    put("SC2220", "Invalid flags are not handled. Add a `*)` case.");
    put("SC2221", "This pattern always overrides a later one.");
    put("SC2222", "This pattern never matches because of a previous pattern.");
    put("SC2223", "This default assignment may cause DoS due to globbing. Quote it.");
    put("SC2224", "This mv has no destination. Check the arguments.");
    put("SC2225", "This cp has no destination. Check the arguments.");
    put("SC2226", "This ln has no destination. Check the arguments, or specify '.' explicitly.");
    put("SC2227", "Redirection applies to the find command itself. Rewrite to work per action (or move to end).");
    put("SC2229", "This does not read 'foo'. Remove $/${} for that, or use ${var?} to quiet.");
    put("SC2230", "which is non-standard. Use builtin 'command -v' instead.");
    put("SC2231", "Quote expansions in this for loop glob to prevent wordsplitting, e.g. \"$dir\"/*.txt .");
    put("SC2232", "Can't use sudo with builtins like cd. Did you want sudo sh -c .. instead?");
    put("SC2233", "Remove superfluous `(..)` around condition.");
    put("SC2234", "Remove superfluous `(..)` around test command.");
    put("SC2235", "Use `{ ..; }` instead of `(..)` to avoid subshell overhead.");
    put("SC2236", "Use `-n` instead of `! -z`.");
    put("SC2237", "Use `[ -n .. ]` instead of `! [ -z .. ]`.");
    put("SC2238", "Redirecting to/from command name instead of file. Did you want pipes/xargs (or quote to ignore)?");
    put("SC2239", "Ensure the shebang uses the absolute path to the interpreter.");
    put("SC2240", "The dot command does not support arguments in sh/dash. Set them as variables.");
    put("SC2241", "The exit status can only be one integer 0-255. Use stdout for other data.");
    put("SC2242", "Can only exit with status 0-255. Other data should be written to stdout/stderr.");
    put("SC2243", "Prefer explicit -n to check for output (or run command without [/[[ to check for success)");
    put("SC2244", "Prefer explicit -n to check non-empty string (or use =/-ne to check boolean/integer).");
    put("SC2245", "-d only applies to the first expansion of this glob. Use a loop to check any/all.");
    put("SC2246", "This shebang specifies a directory. Ensure the interpreter is a file.");
    put("SC2247", "Flip leading $ and \" if this should be a quoted substitution.");
    put("SC2249", "Consider adding a default *) case, even if it just exits with error.");
  }};

  static int calcOffset(CharSequence sequence, int startOffset, int column) {
    int i = 1;
    while (i < column) {
      int c = Character.codePointAt(sequence, startOffset);
      i += c == '\t' ? 8 : 1;
      startOffset++;
    }
    return startOffset;
  }
}
