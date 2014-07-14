interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Type {
	private ClassChild myClassChild;
	private ClassParent myClassParent;
	private FaceChild myFaceChild;
	private FaceParent myFaceParent;

	public void meth(ClassChild p) {
		myClassChild = p;
		myClassParent = p;
		myFaceChild = p;
		myFaceParent = p;
	}
}