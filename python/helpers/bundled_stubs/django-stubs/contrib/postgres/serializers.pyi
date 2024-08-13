from django.db.migrations.serializer import BaseSerializer

class RangeSerializer(BaseSerializer):
    def serialize(self) -> tuple[str, set[str]]: ...
