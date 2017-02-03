# Stubs for sqlalchemy.schema (Python 2)

from .sql import base
from .sql import schema
from .sql import naming
from .sql import ddl
from .sql import elements

SchemaVisitor = base.SchemaVisitor
CheckConstraint = schema.CheckConstraint
Column = schema.Column
ColumnDefault = schema.ColumnDefault
Constraint = schema.Constraint
DefaultClause = schema.DefaultClause
DefaultGenerator = schema.DefaultGenerator
FetchedValue = schema.FetchedValue
ForeignKey = schema.ForeignKey
ForeignKeyConstraint = schema.ForeignKeyConstraint
Index = schema.Index
MetaData = schema.MetaData
PassiveDefault = schema.PassiveDefault
PrimaryKeyConstraint = schema.PrimaryKeyConstraint
SchemaItem = schema.SchemaItem
Sequence = schema.Sequence
Table = schema.Table
ThreadLocalMetaData = schema.ThreadLocalMetaData
UniqueConstraint = schema.UniqueConstraint
_get_table_key = schema._get_table_key
ColumnCollectionConstraint = schema.ColumnCollectionConstraint
ColumnCollectionMixin = schema.ColumnCollectionMixin
conv = elements.conv
DDL = ddl.DDL
CreateTable = ddl.CreateTable
DropTable = ddl.DropTable
CreateSequence = ddl.CreateSequence
DropSequence = ddl.DropSequence
CreateIndex = ddl.CreateIndex
DropIndex = ddl.DropIndex
CreateSchema = ddl.CreateSchema
DropSchema = ddl.DropSchema
_DropView = ddl._DropView
CreateColumn = ddl.CreateColumn
AddConstraint = ddl.AddConstraint
DropConstraint = ddl.DropConstraint
DDLBase = ddl.DDLBase
DDLElement = ddl.DDLElement
_CreateDropBase = ddl._CreateDropBase
_DDLCompiles = ddl._DDLCompiles
sort_tables = ddl.sort_tables
sort_tables_and_constraints = ddl.sort_tables_and_constraints
