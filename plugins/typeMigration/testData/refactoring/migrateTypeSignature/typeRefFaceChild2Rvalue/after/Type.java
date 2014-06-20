interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Type {
	private FaceChild myField;
	public void meth(ClassChild pcc, ClassParent pcp, FaceChild pfc, FaceChild pfp) {
		myField = pcc;
		myField = pcp;
		myField = pfc;
		myField = pfp;
	}
}
