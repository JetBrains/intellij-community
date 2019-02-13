// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MavenLogOutputParserTest extends UsefulTestCase {

  private ExternalSystemTaskId taskId;
  private MavenLogOutputParser myParser;
  @Mock private MavenLoggedEventParser parser1;
  @Mock private MavenLoggedEventParser parser2;
  @Mock private BuildOutputInstantReader reader;
  @Mock private Consumer<BuildEvent> consumer;


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    taskId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, EXECUTE_TASK, "PROJECT_ID");
    myParser = new MavenLogOutputParser(taskId, ContainerUtil.list(parser1, parser2));
    when(parser1.supportsType(any())).thenReturn(true);
    when(parser2.supportsType(any())).thenReturn(true);
  }

  public void testParserShouldCallOnlyFirstParserIfParsed() {
    givenParser1Result(true);
    myParser.parse("", reader, consumer);
    verify(parser1).checkLogLine(taskId, "", null, consumer);
    verify(parser2, never()).checkLogLine(any(), any(), any(), any());
  }

  public void testParserShouldCallSecondIfFirstFailed() {
    givenParser1Result(false);
    myParser.parse("", reader, consumer);
    verify(parser1).checkLogLine(taskId, "", null, consumer);
    verify(parser2).checkLogLine(taskId, "", null, consumer);
  }


  public void testSendLineWithoutPrefixAsNullType() {
    givenParser1Result(true);
    myParser.parse("Some line without prefix", reader, consumer);
    verify(parser1).checkLogLine(taskId, "Some line without prefix", null, consumer);
  }

  public void testSendLineWithInfoPrefix() {
    givenParser1Result(true);
    myParser.parse("[INFO] Some line with info prefix", reader, consumer);
    verify(parser1).checkLogLine(taskId, "Some line with info prefix", LogMessageType.INFO, consumer);
  }

  public void testSendLineWithWarnPrefix() {
    givenParser1Result(true);
    myParser.parse("[WARNING] Some line with info prefix", reader, consumer);
    verify(parser1).checkLogLine(taskId, "Some line with info prefix", LogMessageType.WARNING, consumer);
  }

  public void testSendLineWithErrPrefix() {
    givenParser1Result(true);
    myParser.parse("[ERROR] Some line with err prefix", reader, consumer);
    verify(parser1).checkLogLine(taskId, "Some line with err prefix", LogMessageType.ERROR, consumer);
  }

  public void testShouldClearLineWithProgress() {
    givenParser1Result(true);
    myParser.parse("Progress 1\r Progress 2\r Progress 3\r[INFO] Done", reader, consumer);
    verify(parser1).checkLogLine(taskId, "Done", LogMessageType.INFO, consumer);
  }



  private void givenParser1Result(boolean result) {
    when(parser1.checkLogLine(any(), any(), any(), any())).thenReturn(result);
  }
}