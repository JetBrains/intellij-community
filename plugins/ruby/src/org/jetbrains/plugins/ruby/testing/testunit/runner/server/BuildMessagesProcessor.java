package org.jetbrains.plugins.ruby.testing.testunit.runner.server;

import jetbrains.buildServer.messages.BuildMessage1;

import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public interface BuildMessagesProcessor {


  void process(final List<BuildMessage1> messages);
}
