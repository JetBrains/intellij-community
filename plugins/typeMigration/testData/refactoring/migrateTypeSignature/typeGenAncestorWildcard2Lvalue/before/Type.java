import java.util.Set;

interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Set<Ancestor> myAncestors;
	private Set<? extends Ancestor> myAncestorExtends;
	private Set<? super Ancestor> myAncestorSupers;

	private Set<Subject> mySubjects;
	private Set<? extends Subject> mySubjectExtends;
	private Set<? super Subject> mySubjectSupers;

	private Set<Descendant> myDescendants;
	private Set<? extends Descendant> myDescendantExtends;
	private Set<? super Descendant> myDescendantSupers;

	private Set mySet;

	public void meth(Set p) {
		myAncestors = p;
		myAncestorExtends = p;
		myAncestorSupers = p;

		mySubjects = p;
		mySubjectExtends = p;
		mySubjectSupers = p;

		myDescendants = p;
		myDescendantExtends = p;
		myDescendantSupers = p;

		mySet = p;
	}
}
