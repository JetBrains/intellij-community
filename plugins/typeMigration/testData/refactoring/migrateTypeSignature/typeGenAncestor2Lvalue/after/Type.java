import java.util.Collection;
import java.util.Set;

interface Ancestor {}
interface Subject extends Ancestor {}
class Descendant implements Subject {}

class Type {
	private Set<Subject> myAncestors;
	private Set<? extends Ancestor> myAncestorExtends;
	private Set<Subject> myAncestorSupers;

	private Set<Subject> mySubjects;
	private Set<? extends Subject> mySubjectExtends;
	private Set<? super Subject> mySubjectSupers;

	private Set<Subject> myDescendants;
	private Set<Subject> myDescendantExtends;
	private Set<? super Descendant> myDescendantSupers;

	private Set mySet;
	private Collection<Subject> myCollection;

	public void meth(Set<Subject> p) {
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
		myCollection = p;
	}
}
