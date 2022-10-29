//CONF: lombok.accessors.capitalization = beanspec
@lombok.Builder(setterPrefix = "set")
class BuilderWithJavaBeansSpecCapitalization {
	@lombok.Singular("z") java.util.List<String> a;
	@lombok.Singular("yField") java.util.List<String> aField;
	String bField;
}
