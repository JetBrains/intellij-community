package com.jetbrains.python.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiListLikeElement
import com.intellij.psi.util.findParentInFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyCaptureContext.Companion.getCaptureType
import com.jetbrains.python.psi.impl.PyBuiltinCache.Companion.getInstance
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.psi.types.PyLiteralType.Companion.upcastLiteralToClass

class PyMappingPatternImpl(astNode: ASTNode?) : PyElementImpl(astNode), PyMappingPattern, PsiListLikeElement {
  override fun acceptPyVisitor(pyVisitor: PyElementVisitor) {
    pyVisitor.visitPyMappingPattern(this)
  }

  override fun getComponents(): List<PyKeyValuePattern> = findChildrenByClass(PyKeyValuePattern::class.java).toList()

  override fun canExcludePatternType(context: TypeEvalContext): Boolean = false

  override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? {
    val keyTypes = mutableListOf<PyType?>()
    val valueTypes = mutableListOf<PyType?>()
    for (it in components) {
      keyTypes.add(context.getType(it.keyPattern))
      if (it.valuePattern != null) {
        valueTypes.add(context.getType(it.valuePattern!!))
      }
    }

    val patternMappingType = wrapInMappingType(PyUnionType.union(keyTypes), PyUnionType.union(valueTypes))

    val filteredType = getCaptureType(this, context).toList().filter { captureType: PyType? ->
      val mappingType = PyTypeUtil.convertToType(captureType, "typing.Mapping", this, context) ?: return@filter false
      PyTypeChecker.match(mappingType, patternMappingType, context)
    }.let { 
      PyUnionType.union(it) 
    }

    return filteredType ?: patternMappingType
  }

  override fun getCaptureTypeForChild(pattern: PyPattern, context: TypeEvalContext): PyType? {
    val sequenceMember = pattern.findParentInFile(withSelf = true) { this === it.parent }
    if (sequenceMember is PyDoubleStarPattern) {
      val mappingType = PyTypeUtil.convertToType(context.getType(this), "typing.Mapping", pattern, context)
      if (mappingType is PyCollectionType) {
        val dict = getInstance(pattern).getClass("dict") ?: return null
        return PyCollectionTypeImpl(dict, false, mappingType.getElementTypes())
      }
      return null
    }
    
    if (sequenceMember !is PyKeyValuePattern) return null
    
    return getCaptureType(this, context).toList()
      .map { possibleMapping -> possibleMapping.getValueType(sequenceMember, context) }
      .let { PyUnionType.union(it) }
  }

  private fun wrapInMappingType(keyType: PyType?, valueType: PyType?): PyType? {
    val sequence = PyPsiFacade.getInstance(getProject()).createClassByQName("typing.Mapping", this) ?: return null
    return PyCollectionTypeImpl(sequence, false, listOf(keyType, valueType).map { upcastLiteralToClass(it) })
  }
}

private fun PyType?.getValueType(sequenceMember: PyKeyValuePattern, context: TypeEvalContext): PyType? {
  if (this is PyTypedDictType) {
    val key = sequenceMember.getKeyString(context)
    if (key != null) return this.getElementType(key)
  }
  val mappingType = PyTypeUtil.convertToType(this, "typing.Mapping", sequenceMember, context)
                    ?: return PyNeverType.NEVER
  if (mappingType is PyCollectionType) {
    return mappingType.elementTypes[1]
  }
  return null
}

private fun PyKeyValuePattern.getKeyString(context: TypeEvalContext): String? {
  val keyType = context.getType(keyPattern)
  if (keyType is PyLiteralType && keyType.expression is PyStringLiteralExpression) {
    return keyType.expression.getStringValue()
  }
  return null
}
