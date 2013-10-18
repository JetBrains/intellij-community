class Payout:
    @classmethod
    def create(cls, teacher_id, entries):
        payout = cls()
        # payout.teacher = Teacher(teacher_id)
        payout.save() # ok

    def save(self):
        pass
