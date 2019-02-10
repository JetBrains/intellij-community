// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.SelfDescribing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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
      ContainerUtil.addAll(myLines, lines);
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


    public void check() {
      check(false);
    }

    public void check(boolean checkFinishEvent) {
      CollectConsumer collectConsumer = new CollectConsumer();
      MavenLogOutputParser parser =
        new MavenLogOutputParser(ExternalSystemTaskId.create(MavenConstants.SYSTEM_ID, EXECUTE_TASK, "project"), myParsers);

      for (String line : myLines) {
        parser.parse(line, null, collectConsumer);
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
  private static Pair<String, Matcher<BuildEvent>> event(String message, Function<String, BaseMatcher<BuildEvent>> creator) {
    return Pair.create(message, creator.apply(message));
  }

  private class FinishSuccessEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
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

  private class StartEventMatcher extends BaseMatcher<BuildEvent> implements SelfDescribing {
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
}
