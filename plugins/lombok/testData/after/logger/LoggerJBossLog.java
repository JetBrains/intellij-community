class LoggerJBossLog {
  @java.lang.SuppressWarnings("all")
  private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLog.class);
}

class LoggerJBossLogWithImport {
  @java.lang.SuppressWarnings("all")
  private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogWithImport.class);
}

class LoggerJBossLogOuter {
  static class Inner {
    @java.lang.SuppressWarnings("all")
    private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(Inner.class);
  }
}

class LoggerJBossLogWithDifferentLoggerName {
  @java.lang.SuppressWarnings("all")
  private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger("DifferentLogger");
}

class LoggerJBossLogAccessPublic {
	@java.lang.SuppressWarnings("all")
	public static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogAccessPublic.class);
}

class LoggerJBossLogAccessModule {
	@java.lang.SuppressWarnings("all")
	static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogAccessModule.class);
}

class LoggerJBossLogAccessProtected {
	@java.lang.SuppressWarnings("all")
	protected static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogAccessProtected.class);
}

class LoggerJBossLogAccessPackage {
	@java.lang.SuppressWarnings("all")
	static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogAccessPackage.class);
}

class LoggerJBossLogAccessPrivate {
	@java.lang.SuppressWarnings("all")
	private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(LoggerJBossLogAccessPrivate.class);
}

class LoggerJBossLogAccessNone {
}
