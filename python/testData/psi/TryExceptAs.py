try:
    f = open('myfile.txt')
except IOError as (errno, strerror):
    pass
