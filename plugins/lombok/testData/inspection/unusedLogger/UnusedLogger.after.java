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
