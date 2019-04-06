// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.*;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.SelfDescribing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.build.events.MessageEvent.Kind.WARNING;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public abstract class MavenBuildToolLogTestUtils extends UsefulTestCase {

  protected TestCaseBuider testCase(String... lines) {
    return new TestCaseBuider().withLines(lines);
  }

  protected class TestCaseBuider {
    private List<String> myLines = ContainerUtil.newArrayList();
    private List<MavenLoggedEventParser> myParsers = ContainerUtil.newArrayList();
    private List<Pair<String, Matcher<BuildEvent>>> myExpectedEvents = new ArrayList<>();

    public TestCaseBuider withLines(String... lines) {
      List<String> joinedAndSplitted = ContainerUtil.newArrayList(StringUtil.join(lines, "\n").split("\n"));
      ContainerUtil.addAll(myLines, joinedAndSplitted);
      return this;
    }

    public TestCaseBuider withParsers(MavenLoggedEventParser... parsers) {
      ContainerUtil.addAll(myParsers, parsers);
      return this;
    }

    public TestCaseBuider expectSucceed(String message) {
      myExpectedEvents.add(event(message, StartEventMatcher::new));
      myExpectedEvents.add(event(message, FinishSuccessEventMatcher::new));
      return this;
    }

    public TestCaseBuider expect(String message, Function<String, Matcher<BuildEvent>> creator) {
      myExpectedEvents.add(event(message, creator));
      return this;
    }

    public TestCaseBuider expect(String message, Matcher<BuildEvent> matcher) {
      myExpectedEvents.add(Pair.create(message, matcher));
      return this;
    }


    public void check() {
      check(false);
    }

    public void check(boolean checkFinishEvent) {
      CollectConsumer collectConsumer = new CollectConsumer();
      MavenLogOutputParser parser =
        new MavenLogOutputParser(ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, EXECUTE_TASK, "project"), myParsers);

      StubBuildOutputReader reader = new StubBuildOutputReader(myLines);
      String line;
      while ((line = reader.readLine()) != null) {
        parser.parse(line, reader, collectConsumer);
      }

      parser.finish(collectConsumer);


      Iterator<BuildEvent> events = collectConsumer.myReceivedEvents.iterator();
      Iterator<Pair<String, Matcher<BuildEvent>>> expectedEvents = myExpectedEvents.iterator();
      while (events.hasNext()) {
        if (!expectedEvents.hasNext()) {
          BuildEvent next = events.next();
          if (next instanceof FinishBuildEvent && !checkFinishEvent) {
            continue;
          }
          fail("Event: " + next.getMessage() + " was not expected here");
        }

        Pair<String, Matcher<BuildEvent>> matcher = expectedEvents.next();
        assertThat(events.next(), matcher.second);
      }
      if (expectedEvents.hasNext()) {
        fail("Didn't receive expected event: " + expectedEvents.next().first);
      }
    }
  }

  private class CollectConsumer implements Consumer<BuildEvent> {
    private List<BuildEvent> myReceivedEvents = new ArrayList<>();

    @Override
    public void accept(BuildEvent buildEvent) {
      myReceivedEvents.add(buildEvent);
    }
  }

  @NotNull
  public static Pair<String, Matcher<BuildEvent>> event(String message, Function<String, Matcher<BuildEvent>> creator) {
    return Pair.create(message, creator.apply(message));
  }

  public class FinishSuccessEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
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
      description.appendText("Expected successfull FinishEvent " + myMessage);
    }
  }

  public class FinishFailedEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
    private final String myMessage;

    public FinishFailedEventMatcher(String message) {myMessage = message;}

    @Override
    public boolean matches(Object item) {
      return item instanceof FinishEvent
             && ((FinishEvent)item).getMessage().equals(myMessage)
             && ((FinishEvent)item).getResult() instanceof FailureResult;
    }

    @Override
    public void describeTo(@NotNull Description description) {
      description.appendText("Expected failed FinishEvent " + myMessage);
    }
  }

  public class StartEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
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

  public class WarningEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
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

  public class FileEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
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
             && FileUtil.filesEqual(new File(myFileName), ((FileMessageEvent)item).getFilePosition().getFile())
             && ((FileMessageEvent)item).getFilePosition().getStartLine() == myLine
             && ((FileMessageEvent)item).getFilePosition().getStartColumn() == myColumn;
    }

    @Override
    public void describeTo(@NotNull Description description) {
      description.appendText("Expected FileMessageEventImpl \"" + myMessage + "\" at " + myFileName + ":" + myLine + ":" + myColumn);
    }
  }

  private class StubBuildOutputReader implements BuildOutputInstantReader {
    private List<String> myLines;
    private int myPosition = -1;

    public StubBuildOutputReader(List<String> lines) {
      myLines = lines;
    }

    @Override
    public Object getBuildId() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String readLine() {
      myPosition++;
      return getCurrentLine();
    }

    @Override
    public void pushBack() {

    }

    @Override
    public void pushBack(int numberOfLines) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentLine() {
      if (myPosition >= myLines.size() || myPosition < 0) {
        return null;
      }
      return myLines.get(myPosition);
    }


    @Override
    public BuildOutputInstantReader append(CharSequence csq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BuildOutputInstantReader append(CharSequence csq, int start, int end) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BuildOutputInstantReader append(char c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {

    }
  }
}
