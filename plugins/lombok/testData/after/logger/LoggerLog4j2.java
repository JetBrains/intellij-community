class LoggerLog4j2 {
	@SuppressWarnings("all")
	private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(LoggerLog4j2.class);
}
class LoggerLog4j2WithImport {
	@SuppressWarnings("all")
	private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(LoggerLog4j2WithImport.class);
}
class LoggerLog4j2WithDifferentName {
	@SuppressWarnings("all")
	private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger("DifferentName");
}