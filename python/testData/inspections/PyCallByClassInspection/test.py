class Z(object):
    def method(self):
          pass

class A(object):
    def method(self, *args, **kwargs):
        Z.method(<weak_warning descr="Passing inspections.pyCallByClassInspection.test.A instead of inspections.pyCallByClassInspection.test.Z. Is this intentional?">self</weak_warning>) # passing wrong instance
        Z.method(<weak_warning descr="An instance of inspections.pyCallByClassInspection.test.Z expected, not the class itself">Z</weak_warning>) # passing class instead of instance
        Z.method(<weak_warning descr="Passing inspections.pyCallByClassInspection.test.A instead of inspections.pyCallByClassInspection.test.Z. Is this intentional?"><weak_warning descr="An instance of inspections.pyCallByClassInspection.test.Z expected, not the class itself">A</weak_warning></weak_warning>) # passing class instead of instance AND wrong class
        Z.method(Z()) #pass

    def __init__(self):
        pass

class B(A):
    def __init__(self):
        A.__init__(self) # pass

A.method(B()) # pass

