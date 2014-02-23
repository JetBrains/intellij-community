class LoggerJul {
	@SuppressWarnings("all")
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LoggerJul.class.getName());
}
class LoggerJulWithImport {
	@SuppressWarnings("all")
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LoggerJulWithImport.class.getName());
}
class LoggerJulWithDifferentName {
	@SuppressWarnings("all")
	private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger("DifferentName");
}