"""
Tests generic type aliases used in class declarations for
variance incompatibility.
"""

from typing import Generic, TypeVar, TypeAlias

T = TypeVar("T")
T_co = TypeVar("T_co", covariant=True)
T_contra = TypeVar("T_contra", contravariant=True)


class ClassA(Generic[T]):
    pass


A_Alias_1: TypeAlias = ClassA[T_co]
A_Alias_2: TypeAlias = A_Alias_1[T_co]

# Specialized type aliases used within a class declaration should
# result in the same variance incompatibility errors as their
# non-aliased counterparts.

class ClassA_1(ClassA[T_co]):  # E: incompatible variance
    ...


class ClassA_2(A_Alias_1[T_co]):  # E: incompatible variance
    ...


class ClassA_3(A_Alias_2[T_co]):  # E: incompatible variance
    ...



class ClassB(Generic[T, T_co]):
    pass


B_Alias_1 = ClassB[T_co, T_contra]


class ClassB_1(B_Alias_1[T_contra, T_co]):  # E: incompatible variance
    ...
