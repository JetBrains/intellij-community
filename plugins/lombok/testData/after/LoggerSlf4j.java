class LoggerSlf4j {
	@SuppressWarnings("all")
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4j.class);
}
class LoggerSlf4jWithImport {
	@SuppressWarnings("all")
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4jWithImport.class);
}
class LoggerSlf4jOuter {
	static class Inner {
		@SuppressWarnings("all")
		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Inner.class);
	}
}

class LoggerSlf4jWithDifferentLoggerName {
	@SuppressWarnings("all")
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("DifferentLogger");
}