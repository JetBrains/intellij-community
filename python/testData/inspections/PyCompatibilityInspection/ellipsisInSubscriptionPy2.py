import numpy

x = numpy.zeros((3, 4, 5))
y = x[..., 0]  # pass
y = x[..., 0, :]  # pass
