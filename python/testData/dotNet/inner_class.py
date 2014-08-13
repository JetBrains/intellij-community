import clr

clr.AddReferenceByPartialName("SingleNameSpace")

from <caret>SingleNameSpace.Some.Deep.WeHaveClass import MyClass
print MyClass