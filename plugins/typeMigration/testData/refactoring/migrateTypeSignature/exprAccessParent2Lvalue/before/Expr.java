class ClassParent {}
class ClassChild extends ClassParent {}
class ClassGrandChild extends ClassChild {}

class Ession {
	public ClassChild myForSuperAccess;
	public ClassChild forSuperAccess() {
		return myForSuperAccess;
	}
}

class Expr extends Ession {
	public void methMemAcc() {
		ClassChild vfsuper = super.myForSuperAccess;
		ClassChild vmsuper = super.forSuperAccess();

		ClassChild vfcsuper = Expr.super.myForSuperAccess;
		ClassChild vmcsuper = Expr.super.forSuperAccess();
	}
}
