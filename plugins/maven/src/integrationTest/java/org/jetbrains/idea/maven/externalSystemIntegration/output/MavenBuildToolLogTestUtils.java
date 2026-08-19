// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FileMessageEvent;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.StartEvent;
import com.intellij.build.events.SuccessResult;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ThrowableRunnable;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.SelfDescribing;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import static com.intellij.build.events.MessageEvent.Kind.ERROR;
import static com.intellij.build.events.MessageEvent.Kind.WARNING;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Static helpers and {@link Matcher} implementations for {@link MavenLogOutputParser} tests.
 * The actual parsing harness lives in {@link MavenBuildToolLogTester} (parameterized by a {@code Project}), so these
 * tests no longer need the legacy {@code LightIdeaTestCase} base — they can run under JUnit5 {@code @TestApplication}.
 */
public final class MavenBuildToolLogTestUtils {
  private MavenBuildToolLogTestUtils() { }

  public static void failOnWarns(ThrowableRunnable<Throwable> runnable) throws Throwable {
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public boolean processWarn(@NotNull String category, @NotNull String message, Throwable t) {
        fail(message + t);
        return false;
      }
    }, runnable);
  }

  public static String @NotNull [] fromFile(String resource) throws IOException {
    try (InputStream stream = ResourceUtil.getResourceAsStream(MavenBuildToolLogTestUtils.class.getClassLoader(), "", resource);
         Scanner scanner = new Scanner(stream)) {
      List<String> result = new ArrayList<>();
      while (scanner.hasNextLine()) {
        result.add(scanner.nextLine());
      }
      return ArrayUtilRt.toStringArray(result);
    }
  }

  @NotNull
  public static Pair<String, Matcher<BuildEvent>> event(String message, Function<String, Matcher<BuildEvent>> creator) {
    return Pair.create(message, creator.apply(message));
  }

  public static class FinishSuccessEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
    private final String myMessage;

    public FinishSuccessEventMatcher(String message) {myMessage = message;}

    @Override
    public boolean matches(Object item) {
      return item instanceof FinishEvent
             && ((FinishEvent)item).getMessage().equals(myMessage)
             && ((FinishEvent)item).getResult() instanceof SuccessResult;
    }

    @Override
    public void describeTo(@NotNull Description description) {
      description.appendText("Expected successful FinishEvent " + myMessage);
    }
  }

  public static class StartEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
    private final String myMessage;

    public StartEventMatcher(String message) {myMessage = message;}

    @Override
    public boolean matches(Object item) {
      return item instanceof StartEvent
             && ((StartEvent)item).getMessage().equals(myMessage);
    }

    @Override
    public void describeTo(@NotNull Description description) {
      description.appendText("Expected StartEvent " + myMessage);
    }
  }

  public static class WarningEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
    private final String myMessage;

    public WarningEventMatcher(String message) {myMessage = message;}

    @Override
    public boolean matches(Object item) {
      return item instanceof MessageEvent
             && StringUtil.equalsTrimWhitespaces(myMessage, ((MessageEvent)item).getDescription())
             && ((MessageEvent)item).getKind() == WARNING;
    }

    @Override
    public void describeTo(@NotNull Description description) {
      description.appendText("Expected WarningEvent " + myMessage);
    }
  }

  public static class FileEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
    private final String myMessage;
    private final String myFileName;
    private final int myLine;
    private final int myColumn;

    public FileEventMatcher(String message, String fileName, int line, int column) {
      myMessage = message;
      myFileName = fileName;
      myLine = line;
      myColumn = column;
    }

    @Override
    public boolean matches(Object item) {
      return item instanceof FileMessageEvent
             && ((FileMessageEvent)item).getMessage().equals(myMessage)
             && (
               FileUtil.filesEqual(new File(myFileName), ((FileMessageEvent)item).getFilePosition().getFile())
               ||
               FileUtil.filesEqual(new File("/" + myFileName), ((FileMessageEvent)item).getFilePosition().getFile())
             )
             && ((FileMessageEvent)item).getFilePosition().getStartLine() == myLine
             && ((FileMessageEvent)item).getFilePosition().getStartColumn() == myColumn;
    }

    @Override
    public void describeTo(@NotNull Description description) {
      description.appendText("Expected \n" + new FileMessageEventImpl("EXECUTE_TASK:0", ERROR, "Error", myMessage, myMessage,
                                                                      new FilePosition(new File(myFileName), myLine,myColumn)));
    }
  }
}
