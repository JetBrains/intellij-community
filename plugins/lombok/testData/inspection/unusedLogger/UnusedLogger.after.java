// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;

class UnusedLogger {
}

@Slf4j
class UsedLogger {
  void logSomething() {
    log.info("used");
  }
}

class UnusedLoggerWithQualifiedAnnotation {
}

@Slf4j(access = AccessLevel.PUBLIC)
class PublicLogger {
}

@Slf4j(access = AccessLevel.NONE)
class LoggerNotGenerated {
}

class UnusedJavaLogger {
}

@lombok.extern.java.Log
class UsedJavaLogger {
  Object logger() {
    return log;
  }
}

class EnclosingClass {
  static class UnusedNestedLogger {
  }

  @lombok.extern.java.Log
  static class UsedNestedLogger {
    Object logger() {
      return log;
    }
  }
}
