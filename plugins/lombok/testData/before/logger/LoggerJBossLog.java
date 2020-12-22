import lombok.extern.jbosslog.JBossLog;

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