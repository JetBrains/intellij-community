// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.*;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Logger;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.SelfDescribing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.build.events.MessageEvent.Kind.WARNING;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class MavenBuildToolLogTestUtils extends UsefulTestCase {
  protected ExternalSystemTaskId myTaskId;

  public interface ThrowingRunnable {
    void run() throws Throwable;
  }


  public static  void failOnWarns(ThrowingRunnable runnable) throws Throwable {
    LoggedErrorProcessor oldInstance = LoggedErrorProcessor.getInstance();
    try {
      LoggedErrorProcessor.setNewInstance(new LoggedErrorProcessor() {
        @Override
        public void processWarn(String message, Throwable t, @NotNull Logger logger) {
          super.processWarn(message, t, logger);
          fail(message + t);
        }
      });
      runnable.run();
    }
    finally {
      LoggedErrorProcessor.setNewInstance(oldInstance);
    }
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTaskId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, EXECUTE_TASK, "project");
  }

  @NotNull
  protected static String[] fromFile(String resource) throws IOException {
    try (InputStream stream = ResourceUtil.getResourceAsStream(MavenBuildToolLogTestUtils.class, "", resource);
         Scanner scanner = new Scanner(stream)) {
      List<String> result = new ArrayList<>();
      while (scanner.hasNextLine()) {
        result.add(scanner.nextLine());
      }
      return ArrayUtilRt.toStringArray(result);
    }
  }

  protected TestCaseBuider testCase(String... lines) {
    return new TestCaseBuider().withLines(lines);
  }

  protected class TestCaseBuider {
    private List<String> myLines = new ArrayList<>();
    private List<MavenLoggedEventParser> myParsers = new ArrayList<>();
    private List<Pair<String, Matcher<BuildEvent>>> myExpectedEvents = new ArrayList<>();
    private boolean mySkipOutput = false;

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
      Iterator<BuildEvent> events = collect().iterator();
      Iterator<Pair<String, Matcher<BuildEvent>>> expectedEvents = myExpectedEvents.iterator();
      while (events.hasNext()) {

        if (!expectedEvents.hasNext()) {
          BuildEvent next = events.next();

          if (next instanceof FinishBuildEvent && !checkFinishEvent) {
            continue;
          }
          if(next instanceof OutputBuildEvent && mySkipOutput) {
            continue;
          }
          fail("Event: " + next.getMessage() + " was not expected here");
        }

        BuildEvent next = events.next();
        if(next instanceof StartBuildEventImpl && !checkFinishEvent){
          continue;
        }
        if(next instanceof OutputBuildEvent && mySkipOutput) {
          continue;
        }
        Pair<String, Matcher<BuildEvent>> matcher = expectedEvents.next();

        assertThat(next, matcher.second);
      }
      if (expectedEvents.hasNext()) {
        fail("Didn't receive expected event: " + expectedEvents.next().first);
      }
    }


    public String runAndFormatToString() {
      List<BuildEvent> events = collect();

      Map<Object, Integer> levelMap = new HashMap<>();
      Map<Object, String> result = new LinkedHashMap<>();
      for (BuildEvent event : events) {
        if (event instanceof FinishEvent) {
          Integer value = levelMap.remove(event.getId());
          if (value == null) {
            fail("Finish event for non-registered start event" + event);
          }
        }


        if (event instanceof FinishEvent && ((FinishEvent)event).getResult() instanceof FailureResult) {
          result.computeIfPresent(event.getId(), (id, s) -> "error:" + s);
        }
        else {
          int level;
          if (event.getId() instanceof ExternalSystemTaskId) {
            level = 0;
          }
          else {
            Integer integer = levelMap.get(event.getParentId());
            if (integer == null) {
              fail("Parent id not registered!" + event);
            }
            level = integer + 1;
          }
          assertFalse("cannot calculate event level, possible bad parent id", level < 0);
          if(event instanceof OutputBuildEvent && mySkipOutput){
            continue;
          }
          result.put(event.getId(), event.getMessage());
          levelMap.put(event.getId(), level);
        }
      }

      StringBuilder builder = new StringBuilder();
      for (Map.Entry<Object, String> entry : result.entrySet()) {
        builder.append(StringUtil.repeatSymbol(' ', levelMap.get(entry.getKey()))).append(entry.getValue());
        if(!entry.getValue().endsWith("\n")) {
          builder.append("\n");
        }
      }
      return builder.toString();
    }

    private List<BuildEvent> collect() {
      CollectConsumer collectConsumer = new CollectConsumer();
      MavenLogOutputParser parser =
        new MavenLogOutputParser(myTaskId, myParsers);


      collectConsumer.accept(new StartBuildEventImpl(
        new DefaultBuildDescriptor(myTaskId, "Maven Run", System.getProperty("user.dir"), System.currentTimeMillis()), "Maven Run"));
      StubBuildOutputReader reader = new StubBuildOutputReader(myLines);
      String line;
      while ((line = reader.readLine()) != null) {
        parser.parse(line, reader, collectConsumer);
      }

      parser.finish(collectConsumer);
      return collectConsumer.myReceivedEvents;
    }

    public TestCaseBuider withSkippedOutput() {
      mySkipOutput = true;
      return this;
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

    StubBuildOutputReader(List<String> lines) {
      myLines = lines;
    }

    @NotNull
    @Override
    public Object getParentEventId() {
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
  }
}
