from m1 import C

c = C()

print(c.<warning descr="Unresolved attribute reference 'original_class_field' for class 'C'">original_class_field</warning>)
print(c.<warning descr="Unresolved attribute reference 'original_instance_field' for class 'C'">original_instance_field</warning>)
print(c.<warning descr="Unresolved attribute reference 'original_method' for class 'C'">original_method</warning>)

print(c.provided_class_field)
print(c.provided_instance_field)
print(c.provided_method)

print(c.<warning descr="Unresolved attribute reference 'unresolved_attribute' for class 'C'">unresolved_attribute</warning>)
