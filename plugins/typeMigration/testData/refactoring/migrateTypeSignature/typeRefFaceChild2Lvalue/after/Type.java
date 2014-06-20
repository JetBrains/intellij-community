interface FaceParent {}
interface FaceChild extends FaceParent {}
class ClassParent implements FaceChild {}
class ClassChild extends ClassParent {}

class Type {
	private FaceChild myClassChild;
	private FaceChild myClassParent;
	private FaceChild myFaceChild;
	private FaceParent myFaceParent;

	public void meth(FaceChild p) {
		myClassChild = p;
		myClassParent = p;
		myFaceChild = p;
		myFaceParent = p;
	}
}