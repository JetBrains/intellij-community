from m1 import C

c = C()

print(c.class_field)
print(c.instance_field)
print(c.method())

print(c.provided_class_field)
print(c.provided_instance_field)
print(c.provided_method)

print(c.<warning descr="Unresolved attribute reference 'unresolved_attribute' for class 'C'">unresolved_attribute</warning>)
