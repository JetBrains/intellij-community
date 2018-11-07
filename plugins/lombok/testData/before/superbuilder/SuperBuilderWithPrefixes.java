//CONF: lombok.accessors.prefix += m
//CONF: lombok.accessors.prefix += x
@lombok.experimental.SuperBuilder
class SuperBuilderWithPrefixes {
	int mField;
	int xOtherField;
	@lombok.Singular java.util.List<String> mItems;
}
