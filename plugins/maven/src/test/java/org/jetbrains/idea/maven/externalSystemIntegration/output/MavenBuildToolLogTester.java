// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.FinishEvent;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.execution.MavenRunConfigurationType;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;

/**
 * Standalone, {@link Project}-parameterized harness for testing {@link MavenLogOutputParser} pipelines.
 * Extracted from {@link MavenBuildToolLogTestUtils} so it can be used both from the legacy {@code LightIdeaTestCase}-based
 * tests (via {@link MavenBuildToolLogTestUtils#testCase}) and from JUnit5 {@code @TestApplication} tests that obtain a
 * project from a {@code projectFixture}.
 */
public final class MavenBuildToolLogTester {
  private final Project myProject;
  private final ExternalSystemTaskId myTaskId;
  private final List<String> myLines = new ArrayList<>();
  private final List<MavenLoggedEventParser> myParsers = new ArrayList<>();
  private final List<Pair<String, Matcher<BuildEvent>>> myExpectedEvents = new ArrayList<>();
  private boolean mySkipOutput = false;

  public MavenBuildToolLogTester(@NotNull Project project, @NotNull ExternalSystemTaskId taskId) {
    myProject = project;
    myTaskId = taskId;
  }

  public static MavenBuildToolLogTester forProject(@NotNull Project project) {
    return new MavenBuildToolLogTester(project, ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, EXECUTE_TASK, "project"));
  }

  public MavenBuildToolLogTester withLines(String... lines) {
    List<String> joinedAndSplitted = List.of(StringUtil.join(lines, "\n").split("\n"));
    myLines.addAll(joinedAndSplitted);
    return this;
  }

  public MavenBuildToolLogTester withParsers(MavenLoggedEventParser... parsers) {
    ContainerUtil.addAll(myParsers, parsers);
    return this;
  }

  public MavenBuildToolLogTester expectSucceed(String message) {
    myExpectedEvents.add(MavenBuildToolLogTestUtils.event(message, MavenBuildToolLogTestUtils.StartEventMatcher::new));
    myExpectedEvents.add(MavenBuildToolLogTestUtils.event(message, MavenBuildToolLogTestUtils.FinishSuccessEventMatcher::new));
    return this;
  }

  public MavenBuildToolLogTester expect(String message, Function<String, Matcher<BuildEvent>> creator) {
    myExpectedEvents.add(MavenBuildToolLogTestUtils.event(message, creator));
    return this;
  }

  public MavenBuildToolLogTester expect(String message, Matcher<BuildEvent> matcher) {
    myExpectedEvents.add(Pair.create(message, matcher));
    return this;
  }

  public MavenBuildToolLogTester withSkippedOutput() {
    mySkipOutput = true;
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
        if (next instanceof OutputBuildEvent && mySkipOutput) {
          continue;
        }
        fail("Event: " + next.getMessage() + " was not expected here");
      }

      BuildEvent next = events.next();
      if (next instanceof StartBuildEventImpl && !checkFinishEvent) {
        continue;
      }
      if (next instanceof OutputBuildEvent && mySkipOutput) {
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
        Integer value = levelMap.get(event.getId());
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
        assertFalse(level < 0, "cannot calculate event level, possible bad parent id");
        if (event instanceof OutputBuildEvent && mySkipOutput) {
          continue;
        }
        result.put(event.getId(), event.getMessage());
        levelMap.put(event.getId(), level);
      }
    }

    StringBuilder builder = new StringBuilder();
    for (Map.Entry<Object, String> entry : result.entrySet()) {
      Integer indent = levelMap.get(entry.getKey());
      builder.append(StringUtil.repeatSymbol(' ', indent == null ? 0 : indent.intValue())).append(entry.getValue());
      if (!entry.getValue().endsWith("\n")) {
        builder.append("\n");
      }
    }
    return builder.toString();
  }

  private List<BuildEvent> collect() {
    MavenRunConfiguration configuration =
      (MavenRunConfiguration)new MavenRunConfigurationType.MavenRunConfigurationFactory(MavenRunConfigurationType.getInstance())
        .createTemplateConfiguration(myProject);
    CollectConsumer collectConsumer = new CollectConsumer();
    MavenLogOutputParser parser = new MavenLogOutputParser(configuration, myTaskId, myParsers);

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

  private static class CollectConsumer implements Consumer<BuildEvent> {
    private final List<BuildEvent> myReceivedEvents = new ArrayList<>();

    @Override
    public void accept(BuildEvent buildEvent) {
      myReceivedEvents.add(buildEvent);
    }
  }

  private static class StubBuildOutputReader implements BuildOutputInstantReader {
    private final List<String> myLines;
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

    private String getCurrentLine() {
      if (myPosition >= myLines.size() || myPosition < 0) {
        return null;
      }
      return myLines.get(myPosition);
    }
  }
}
