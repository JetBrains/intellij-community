from dataclasses import dataclass

@dataclass
class AnimalClass:
    number_legs: int = None

@dataclass
class MamaClass(AnimalClass):
    pass

@dataclass
class CatClass(MamaClass):
    pet_name: str = None