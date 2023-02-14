old_set = {(a := 1), (b := 1)}    # valid
new_set = {a := 1, b := 2, c := 3}  # valid

my_list = [1, 2, 3]
set_comp_old = {(a := my_list[i]) for i in my_list if (k := i) > 0}    # valid
set_comp_new = {b := my_list[j] for j in my_list if (k := j) > 0}     # valid
set_comp_new_invalid = {b := my_list[j] for j in my_list if k := True}     # invalid