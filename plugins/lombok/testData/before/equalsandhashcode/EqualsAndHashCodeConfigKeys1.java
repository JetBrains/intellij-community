//CONF: lombok.equalsAndHashCode.callSuper = skip

@lombok.EqualsAndHashCode
class EqualsAndHashCodeConfigKeys1Parent {
}

@lombok.EqualsAndHashCode
class EqualsAndHashCodeConfigKeys1 extends EqualsAndHashCodeConfigKeys1Parent {
	int x;
}
