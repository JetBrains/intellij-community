import lombok.extern.slf4j.Slf4j;

@lombok.extern.slf4j.Slf4j
class LoggerSlf4j {
}

@Slf4j
class LoggerSlf4jWithImport {
}

class LoggerSlf4jOuter {
	@lombok.extern.slf4j.Slf4j
	static class Inner {
		
	}
}