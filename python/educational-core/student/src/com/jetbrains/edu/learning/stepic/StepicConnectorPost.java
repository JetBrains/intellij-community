/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.edu.learning.stepic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

// Using postToStepic via ourClient garant authorization
public class StepicConnectorPost {
  private static final Logger LOG = Logger.getInstance(StepicConnectorPost.class.getName());

  // TODO All methods must be rewrite by this, else NPE from ourClient
  static boolean postToStepic(String link, AbstractHttpEntity entity) throws IOException {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + link);
    request.setEntity(entity);

    final CloseableHttpResponse response = StepicConnectorLogin.getHttpClient().execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepic returned non 200 status code " + responseString);
    }
    return true;
  }


  // TODO realise
  public static void postAttempt(@NotNull final Task task) {
    if (task.getStepicId() <= 0) {
      return;
    }

    final HttpPost attemptRequest = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.ATTEMPTS);
    //setHeaders(attemptRequest, "application/json");
    String attemptRequestBody = new Gson().toJson(new StepicWrappers.AttemptWrapper(task.getStepicId()));
    attemptRequest.setEntity(new StringEntity(attemptRequestBody, ContentType.APPLICATION_JSON));

    try {
      final CloseableHttpResponse attemptResponse = StepicConnectorLogin.getHttpClient().execute(attemptRequest);
      final HttpEntity responseEntity = attemptResponse.getEntity();
      final String attemptResponseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      final StatusLine statusLine = attemptResponse.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_CREATED) {
        LOG.error("Failed to make attempt " + attemptResponseString);
      }
      final StepicWrappers.AttemptWrapper.Attempt attempt =
        new Gson().fromJson(attemptResponseString, StepicWrappers.AttemptContainer.class).attempts.get(0);

      final Map<String, TaskFile> taskFiles = task.getTaskFiles();
      final ArrayList<StepicWrappers.SolutionFile> files = new ArrayList<StepicWrappers.SolutionFile>();
      for (TaskFile fileEntry : taskFiles.values()) {
        files.add(new StepicWrappers.SolutionFile(fileEntry.name, fileEntry.text));
      }
      //postSubmission(passed, attempt, files);
      postSubmission(true, attempt, files);
    }
    catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

  // TODO realise
  private static void postSubmission(boolean passed,
                                     StepicWrappers.AttemptWrapper.Attempt attempt,
                                     ArrayList<StepicWrappers.SolutionFile> files) throws IOException {
    final HttpPost request = new HttpPost(EduStepicNames.STEPIC_API_URL + EduStepicNames.SUBMISSIONS);

    String requestBody = new Gson().toJson(new StepicWrappers.SubmissionWrapper(attempt.id, passed ? "1" : "0", files));
    request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
    final CloseableHttpResponse response = StepicConnectorLogin.getHttpClient().execute(request);
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    final StatusLine line = response.getStatusLine();
    if (line.getStatusCode() != HttpStatus.SC_CREATED) {
      LOG.error("Failed to make submission " + responseString);
    }
  }


  public static boolean enrollToCourse(final int courseId) {

    final StepicWrappers.EnrollmentWrapper enrollment = new StepicWrappers.EnrollmentWrapper(String.valueOf(courseId));
    try {
      return postToStepic(EduStepicNames.ENROLLMENTS, new StringEntity(new GsonBuilder().create().toJson(enrollment)));
    }
    catch (IOException e) {
      LOG.warn("EnrollToCourse error\n" + e.getMessage());
    }
    return false;
  }
}
