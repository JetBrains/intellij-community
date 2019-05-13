class NormalLR:
    def __init__(self, *, tau=None):
        self.tau = tau

class GradientLR(NormalLR):
    def __init__(self, *, tau=None):
        super().__init__(tau=tau)