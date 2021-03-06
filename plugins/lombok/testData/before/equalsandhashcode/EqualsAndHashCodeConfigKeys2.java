//CONF: lombok.equalsAndHashCode.callSuper = call
@lombok.EqualsAndHashCode
class EqualsAndHashCodeConfigKeys2Object extends Object {
}

@lombok.EqualsAndHashCode
class EqualsAndHashCodeConfigKeys2Parent {
}

@lombok.EqualsAndHashCode
class EqualsAndHashCodeConfigKeys2 extends EqualsAndHashCodeConfigKeys2Parent {
	int x;
}
