package com.jetbrains.python.packaging;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyExternalProcessException extends Exception {
  private static final Pattern WITH_CR_DELIMITER_PATTERN = Pattern.compile("(?<=\r|\n|\r\n)");

  private final int myRetcode;
  @NotNull private String myName;
  @NotNull private List<String> myArgs;
  @NotNull private String myMessage;

  public PyExternalProcessException(int retcode, @NotNull String name, @NotNull List<String> args, @NotNull String message) {
    super(String.format("External process error '%s %s':\n%s", name, StringUtil.join(args, " "), message));
    myRetcode = retcode;
    myName = name;
    myArgs = args;
    myMessage = stripLinesWithoutLineFeeds(message);
  }

  public int getRetcode() {
    return myRetcode;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public List<String> getArgs() {
    return myArgs;
  }

  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @NotNull
  private static String stripLinesWithoutLineFeeds(@NotNull String s) {
    final String[] lines = WITH_CR_DELIMITER_PATTERN.split(s);
    final List<String> result = new ArrayList<String>();
    for (String line : lines) {
      if (!line.endsWith("\r")) {
        result.add(line);
      }
    }
    return StringUtil.join(result, "");
  }
}
