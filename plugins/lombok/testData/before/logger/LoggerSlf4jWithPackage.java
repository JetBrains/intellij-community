package before;
@lombok.extern.slf4j.Slf4j
class LoggerSlf4jWithPackage {
}
class LoggerSlf4jWithPackageOuter {
	@lombok.extern.slf4j.Slf4j
	static class Inner {
	}
}