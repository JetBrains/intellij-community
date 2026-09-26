import lombok.extern.slf4j.Slf4j;
import lombok.AccessLevel;

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

@Slf4j(access = AccessLevel.PUBLIC)
class LoggerSlf4jAccessPublic {
}

@Slf4j(access = AccessLevel.MODULE)
class LoggerSlf4jAccessModule {
}

@Slf4j(access = AccessLevel.PROTECTED)
class LoggerSlf4jAccessProtected {
}

@Slf4j(access = AccessLevel.PACKAGE)
class LoggerSlf4jAccessPackage {
}

@Slf4j(access = AccessLevel.PRIVATE)
class LoggerSlf4jAccessPrivate {
}

@Slf4j(access = AccessLevel.NONE)
class LoggerSlf4jAccessNone {
}