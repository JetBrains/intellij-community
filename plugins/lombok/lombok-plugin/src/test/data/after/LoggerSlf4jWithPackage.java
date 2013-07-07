package before;
class LoggerSlf4jWithPackage {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4jWithPackage.class);
}
class LoggerSlf4jWithPackageOuter {
	static class Inner {
		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Inner.class);
	}
}
