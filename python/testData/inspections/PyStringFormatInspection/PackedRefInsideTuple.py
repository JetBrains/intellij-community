tuple = (3, 4)
"{3}".format(*[1, 2, *tuple])
"{4}".format(*[1, 2, *tuple])
"{1}".format(*[1, 2, *tuple])

"{3}".format(*[*tuple, 1, 2])
"{4}".format(*[*tuple, 1, 2])
"{1}".format(*[*tuple, 1, 2])