import lombok.extern.slf4j.Slf4j;

@Slf4j
class LoggerSlf4j {
}

@Slf4j
class LoggerSlf4jWithImport {
}

class LoggerSlf4jOuter {
	@Slf4j
	static class Inner {
		
	}
}

@Slf4j(topic="DifferentLogger")
class LoggerSlf4jWithDifferentLoggerName {
}