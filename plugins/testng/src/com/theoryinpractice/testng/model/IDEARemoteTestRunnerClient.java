/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.theoryinpractice.testng.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import org.testng.remote.strprotocol.*;

import java.net.SocketTimeoutException;

/**
 * The client side of the RemoteTestRunner. Handles the marshaling of the different messages.
 */
public class IDEARemoteTestRunnerClient extends AbstractRemoteTestRunnerClient
{

  private TestNGRemoteListener myListener;
  private int myPort;
  private Project myProject;

  public synchronized void startListening(TestNGConfiguration config) {
      final IMessageSender messageSender = TestNGRunnableState.supportSerializationProtocol(config)
                                           //todo pass ack = true
                                           ? new SerializedMessageSender("localhost", myPort)
                                           : new StringMessageSender("localhost", myPort);
      final ServerConnection srvConnection = new ServerConnection(messageSender) {
        @Override
        protected void handleThrowable(Throwable cause) {
          cause.printStackTrace();
        }

        @Override
        public void run() {
          try {
            messageSender.initReceiver();
          }
          catch (SocketTimeoutException e) {
            e.printStackTrace();
          }
          super.run();
        }
      };
    startListening(new IRemoteSuiteListener[]{myListener},
                     new IRemoteTestListener[]{myListener},
                     srvConnection);
    }

    @Override
    protected void notifyStart(final GenericMessage genericMessage) {
        DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
          public void run() {
            for (final IRemoteSuiteListener listener : m_suiteListeners) {
              listener.onInitialization(genericMessage);
            }
          }
        }, ModalityState.NON_MODAL);
    }

    @Override
    protected void notifySuiteEvents(final SuiteMessage suiteMessage) {
        DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
          public void run() {
            for (final IRemoteSuiteListener listener : m_suiteListeners) {
              if (suiteMessage.isMessageOnStart()) {
                listener.onStart(suiteMessage);
              }
              else {
                listener.onFinish(suiteMessage);
              }
            }
          }
        }, ModalityState.NON_MODAL);
    }

    @Override
    protected void notifyTestEvents(final TestMessage testMessage) {
        DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
          public void run() {
            for (final IRemoteTestListener listener : m_testListeners) {
              if (testMessage.isMessageOnStart()) {
                listener.onStart(testMessage);
              }
              else {
                listener.onFinish(testMessage);
              }
            }
          }
        }, ModalityState.NON_MODAL);
    }

    @Override
    protected void notifyResultEvents(final TestResultMessage testResultMessage) {
        DumbService.getInstance(myProject).smartInvokeLater(new Runnable() {
          public void run() {
            for (final IRemoteTestListener listener : m_testListeners) {
              switch (testResultMessage.getResult()) {
                case MessageHelper.TEST_STARTED:
                  listener.onTestStart(testResultMessage);
                  break;
                case MessageHelper.PASSED_TEST:
                  listener.onTestSuccess(testResultMessage);
                  break;
                case MessageHelper.FAILED_TEST:
                  listener.onTestFailure(testResultMessage);
                  break;
                case MessageHelper.SKIPPED_TEST:
                  listener.onTestSkipped(testResultMessage);
                  break;
                case MessageHelper.FAILED_ON_PERCENTAGE_TEST:
                  listener.onTestFailedButWithinSuccessPercentage(testResultMessage);
                  break;
              }
            }
          }
        }, ModalityState.NON_MODAL);
    }

  public void prepareListening(TestNGRemoteListener listener,
                               Project project,
                               int port) {
    myListener = listener;
    myProject = project;
    myPort = port;
  }

}
