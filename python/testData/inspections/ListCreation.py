<weak_warning descr="Multi-step list initialization can be replaced with a list literal">my_<caret>list = [m]</weak_warning>
my_list.append(1)
my_list.append(var)
my_list.append(my_list)
if bar:                    #PY-2898
  my_list.append("bar")
my_list.append(my_list)
do_something()