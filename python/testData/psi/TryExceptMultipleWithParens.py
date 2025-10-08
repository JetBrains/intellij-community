try:
    f = open('myfile.txt')
except (IOError, OtherError):
    pass
