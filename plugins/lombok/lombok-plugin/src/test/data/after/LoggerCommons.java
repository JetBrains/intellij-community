class LoggerCommons {
	@SuppressWarnings("all")
	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(LoggerCommons.class);
}
class LoggerCommonsWithImport {
	@SuppressWarnings("all")
	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(LoggerCommonsWithImport.class);
}
class LoggerCommonsWithDifferentName {
	@SuppressWarnings("all")
	private static final org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog("DifferentName");
}