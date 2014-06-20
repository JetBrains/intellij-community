class ClassParent {}
class ClassChild extends ClassParent {
	public void forAccess2() {
	}

	public int myForAccess;
}
class ClassGrandChild extends ClassChild {}

class Expr {
	public ClassChild myForAccess;
	public ClassChild forAccess() {
		return myForAccess;
	}

	public void methMemAcc() {
		ClassChild vf = myForAccess;
		ClassChild vm = forAccess();

		ClassChild vfthis = this.myForAccess;
		ClassChild vmthis = this.forAccess();

		ClassChild vfcthis = Expr.this.myForAccess;
		ClassChild vmcthis = Expr.this.forAccess();

		ClassChild vfparen = (this).myForAccess;
		ClassChild vmparen = (this).forAccess();

		ClassChild vfnew = new Expr().myForAccess;
		ClassChild vmnew = new Expr().forAccess();

		int v = forAccess().myForAccess;
		forAccess().forAccess2();
	}
}
