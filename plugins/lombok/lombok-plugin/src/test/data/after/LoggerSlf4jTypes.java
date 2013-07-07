interface LoggerSlf4jTypesInterface {
}
@interface LoggerSlf4jTypesAnnotation {
}
enum LoggerSlf4jTypesEnum {
;
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4jTypesEnum.class);
}
enum LoggerSlf4jTypesEnumWithElement {
	FOO;
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggerSlf4jTypesEnumWithElement.class);
}
interface LoggerSlf4jTypesInterfaceOuter {
	class Inner {
		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Inner.class);
	}
}