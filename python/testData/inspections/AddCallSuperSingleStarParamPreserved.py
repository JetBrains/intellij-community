class NormalLR:
    def __init__(self, *, tau=None):
        self.tau = tau

class GradientLR(NormalLR):
    def <warning descr="Call to __init__ of super class is missed">_<caret>_init__</warning>(self, *, tau=None):
        pass