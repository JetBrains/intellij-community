class ClassParent {}
class ClassChild extends ClassParent {}
class ClassGrandChild extends ClassChild {}

class Ession {
	public ClassGrandChild myForSuperAccess1;
	public ClassGrandChild forSuperAccess1() {
		return null;
	}

	public ClassGrandChild myForSuperAccess2;
	public ClassGrandChild forSuperAccess2() {
		return null;
	}
}

class Expr extends Ession {
	private ClassGrandChild myField;
	public void methMemAcc() {
		myField = super.myForSuperAccess1;
		myField = super.forSuperAccess1();

		myField = Expr.super.myForSuperAccess2;
		myField = Expr.super.forSuperAccess2();
	}
}
