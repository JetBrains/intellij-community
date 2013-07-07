class LoggerSlf4j {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4j.class);
}
class LoggerSlf4jWithImport {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4jWithImport.class);
}
class LoggerSlf4jOuter {
	static class Inner {
		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Inner.class);
	}
}