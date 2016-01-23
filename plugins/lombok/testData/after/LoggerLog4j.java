class LoggerLog4j {
	@SuppressWarnings("all")
	private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LoggerLog4j.class);
}
class LoggerLog4jWithImport {
	@SuppressWarnings("all")
	private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(LoggerLog4jWithImport.class);
}
class LoggerLog4jWithDifferentName {
	@SuppressWarnings("all")
	private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger("DifferentName");
}