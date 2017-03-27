import abc


class A:
    @property
    def <warning descr="Getter should return or yield something">normal_property</warning>(self):
        pass

    @property
    @abc.abstractproperty
    def abstract_property1(self):
        pass

    @property
    @abc.abstractmethod
    def abstract_property2(self):
        pass
