list = [3, 4]
"{3}".format(*[1, 2, *list])
"{4}".format(*[1, 2, *list])
"{1}".format(*[1, 2, *list])

"{3}".format(*[*list, 1, 2])
"{4}".format(*[*list, 1, 2])
"{1}".format(*[*list, 1, 2])