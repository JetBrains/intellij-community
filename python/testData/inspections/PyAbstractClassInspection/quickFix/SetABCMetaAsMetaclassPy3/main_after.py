from abc import ABCMeta

from PyAbstractClassInspection.quickFix.SetABCMetaAsMetaclassPy3.main_import import A1

class A2(A1, metaclass=ABCMeta):
    pass