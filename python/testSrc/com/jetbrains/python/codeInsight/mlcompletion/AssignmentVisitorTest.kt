// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.mlcompletion.prev2calls.AssignmentVisitor
import com.jetbrains.python.codeInsight.mlcompletion.prev2calls.ImportsVisitor
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import junit.framework.TestCase

class AssignmentVisitorTest: PyTestCase() {
  override fun getTestDataPath(): String = super.getTestDataPath() + "/codeInsight/mlcompletion/prev2calls"

  fun testAssignmentVisitorOnePrevCall() = testAssignmentVisitor(arrayListOf(Pair("numpy", "arange")))
  fun testAssignmentVisitorTwoPrevCalls() = testAssignmentVisitor(arrayListOf(
    Pair("numpy", "arange"),
    Pair("numpy.arange", "reshape")))

  fun testAssignmentVisitorTwoDifferentPackages() = testAssignmentVisitor(arrayListOf(
    Pair("matplotlib", "pyplot"),
    Pair("numpy", "random"),
    Pair("numpy.random", "normal"),
    Pair("matplotlib.pyplot", "hist"),
    Pair("matplotlib.pyplot", "show")))

  fun testAssignmentVisitorTwoDifferentImportTypes() = testAssignmentVisitor(arrayListOf(
    Pair("tensorflow", "contrib"),
    Pair("tensorflow.contrib", "factorization"),
    Pair("tensorflow.contrib.factorization", "KMeans"),
    Pair("tensorflow", "placeholder"),
    Pair("tensorflow", "float32"),
    Pair("tensorflow.contrib.factorization.KMeans", "training_graph"),
    Pair("", "print")))

  fun testAssignmentVisitorTwoDifferentKindOfImportsAndPackages() = testAssignmentVisitor(arrayListOf(
    Pair("tensorflow", "examples"),
    Pair("tensorflow.examples", "tutorials"),
    Pair("tensorflow.examples.tutorials", "mnist"),
    Pair("tensorflow.examples.tutorials.mnist.input_data", "read_data_sets"),
    Pair("numpy", "zeros"),
    Pair("", "range"),
    Pair("tensorflow.examples.tutorials.mnist.input_data.read_data_sets", "train"),
    Pair("tensorflow.examples.tutorials.mnist.input_data.read_data_sets.train", "labels")))

  fun testAssignmentVisitorCheckInArguments() = testAssignmentVisitor(arrayListOf(
    Pair("numpy", "argmax"),
    Pair("tensorflow", "convert_to_tensor"),
    Pair("tensorflow", "nn"),
    Pair("tensorflow.nn", "embedding_lookup"),
    Pair("tensorflow", "equal"),
    Pair("tensorflow", "cast"),
    Pair("tensorflow", "argmax"),
    Pair("tensorflow", "int32"),
    Pair("tensorflow", "reduce_mean"),
    Pair("tensorflow", "cast"),
    Pair("tensorflow", "float32")))

  fun testAssignmentVisitorCheckAnotherPackage() = testAssignmentVisitor(arrayListOf(
    Pair("pandas", "read_csv"),
    Pair("pandas", "compat"),
    Pair("pandas.compat", "StringIO"),
    Pair("pandas.read_csv", "apply")))

  fun testAssignmentVisitorCallArgumentsOrder() = testAssignmentVisitor(arrayListOf(
    Pair("numpy", "arange"),
    Pair("numpy", "maximum"),
    Pair("numpy.arange", "reshape"),
    Pair("numpy", "minimum"),
    Pair("numpy", "absolute"),
    Pair("numpy.arange.reshape", "reshape")))

  fun testAssignmentVisitorClassSelfFields() = testAssignmentVisitor(arrayListOf(
    Pair("numpy", "arange"),
    Pair("numpy.arange", "reshape")))

  fun testAssignmentVisitorCallAndReferenceArgumentsOrder() = testAssignmentVisitor(arrayListOf(
    Pair("elem", "call1"),
    Pair("elem", "field1"),
    Pair("elem.call1", "ref1"),
    Pair("elem.call1.ref1", "call2"),
    Pair("elem", "field2"),
    Pair("elem.call1.ref1.call2", "ref2"),
    Pair("elem.call1.ref1.call2.ref2", "ref3"),
    Pair("elem.call1.ref1.call2.ref2.ref3", "call3")))

  private fun testAssignmentVisitor(expectedPrevCalls: ArrayList<Pair<String, String>>) {
    val lookup = invokeCompletionAndGetLookup()
    val scope =
      PsiTreeUtil.getParentOfType(lookup.psiElement!!, PyFunction::class.java, PyClass::class.java, PyFile::class.java)!!
    val importsVisitor = ImportsVisitor()
    lookup.psiFile!!.accept(importsVisitor)
    val assignmentsVisitor = AssignmentVisitor(lookup.lookupStart, scope, importsVisitor.fullNames)
    scope.accept(assignmentsVisitor)
    val actualPrevCalls = ArrayList<Pair<String, String>>()
    assignmentsVisitor.arrPrevCalls.forEachIndexed { i, it ->
      actualPrevCalls.add(Pair(it.qualifier, it.reference))
    }
    TestCase.assertEquals(expectedPrevCalls, actualPrevCalls)
  }

  private fun invokeCompletionAndGetLookup(): LookupImpl {
    myFixture.configureByFile(getTestName(true) + ".py")
    myFixture.completeBasic()
    return myFixture.lookup as LookupImpl
  }
}