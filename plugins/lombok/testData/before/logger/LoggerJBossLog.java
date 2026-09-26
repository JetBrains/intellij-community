import lombok.extern.jbosslog.JBossLog;
import lombok.AccessLevel;

@lombok.extern.jbosslog.JBossLog
class LoggerJBossLog {
}

@JBossLog
class LoggerJBossLogWithImport {
}

class LoggerJBossLogOuter {
  @lombok.extern.jbosslog.JBossLog
  static class Inner {

  }
}

@JBossLog(topic="DifferentLogger")
class LoggerJBossLogWithDifferentLoggerName {
}

@JBossLog(access = AccessLevel.PUBLIC)
class LoggerJBossLogAccessPublic {
}

@JBossLog(access = AccessLevel.MODULE)
class LoggerJBossLogAccessModule {
}

@JBossLog(access = AccessLevel.PROTECTED)
class LoggerJBossLogAccessProtected {
}

@JBossLog(access = AccessLevel.PACKAGE)
class LoggerJBossLogAccessPackage {
}

@JBossLog(access = AccessLevel.PRIVATE)
class LoggerJBossLogAccessPrivate {
}

@JBossLog(access = AccessLevel.NONE)
class LoggerJBossLogAccessNone {
}