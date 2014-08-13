class Car:
    color = ""
    def description(self):
        description_string = "This is a %s car." % self.color    # we'll explain self parameter later in task 4
        return description_string

car1 = Car()
car2 = create object of Car

car1.color = "blue"
set car2 color

print(car1.description())
print(car2.description())
