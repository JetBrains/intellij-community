class LoggerXSlf4j {
	@SuppressWarnings("all")
	private static final org.slf4j.ext.XLogger log = org.slf4j.ext.XLoggerFactory.getXLogger(LoggerXSlf4j.class);
}
class LoggerXSlf4jWithImport {
	@SuppressWarnings("all")
	private static final org.slf4j.ext.XLogger log = org.slf4j.ext.XLoggerFactory.getXLogger(LoggerXSlf4jWithImport.class);
}
class LoggerXSlf4jWithDifferentName {
	@SuppressWarnings("all")
	private static final org.slf4j.ext.XLogger log = org.slf4j.ext.XLoggerFactory.getXLogger("DifferentName");
}