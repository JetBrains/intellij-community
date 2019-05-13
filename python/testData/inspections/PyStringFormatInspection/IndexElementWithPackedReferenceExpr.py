old_dict = {1: "b"}
"{d[2]}".format(d={**old_dict, 2: "a"})
"{d[1]}".format(d={**old_dict, 2: "a"})