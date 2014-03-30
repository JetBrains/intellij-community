my_list = [m, 1, var]
my_list.append(my_list)
if bar:                    #PY-2898
  my_list.append("bar")
my_list.append(my_list)
do_something()