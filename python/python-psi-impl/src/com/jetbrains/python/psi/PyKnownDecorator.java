package com.jetbrains.python.psi;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;

// TODO provide more information about these decorators: attributes (e.g. lru_cache(f).cache_info), side-effects etc.
@SuppressWarnings("SpellCheckingInspection")
public class PyKnownDecorator {
  private final QualifiedName myQualifiedName;
  private final boolean myIsProperty;
  private final boolean myIsMutableProperty;
  private final boolean myIsGeneratorBasedCoroutine;
  private final boolean myIsAbstract;
  private final boolean myIsClassMethod;
  private final boolean myIsStaticMethod;

  public PyKnownDecorator(@NotNull String qualifiedName) {
    myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    myIsProperty = false;
    myIsMutableProperty = false;
    myIsGeneratorBasedCoroutine = false;
    myIsAbstract = false;
    myIsClassMethod = false;
    myIsStaticMethod = false;
  }

  private PyKnownDecorator(@NotNull String qualifiedName, boolean isProperty, boolean isMutableProperty, boolean isGeneratorBasedCoroutine, boolean isAbstract,
                           boolean isClassMethod,
                           boolean isStaticMethod) {
    myQualifiedName = QualifiedName.fromDottedString(qualifiedName);
    myIsProperty = isProperty;
    myIsMutableProperty = isMutableProperty;
    myIsGeneratorBasedCoroutine = isGeneratorBasedCoroutine;
    myIsAbstract = isAbstract;
    myIsClassMethod = isClassMethod;
    myIsStaticMethod = isStaticMethod;
  }

  public static class Builder {
    private boolean isProperty = false;
    private boolean isMutableProperty = false;
    private boolean isGeneratorBasedCoroutine = false;
    private boolean isAbstract = false;
    private boolean isClassMethod = false;
    private boolean isStaticMethod = false;
    private final String qualifiedName;

    public Builder(String name) {
      qualifiedName = name;
    }

    public @NotNull Builder setProperty() {
      isProperty = true;
      return this;
    }

    public @NotNull Builder setMutableProperty() {
      isMutableProperty = true;
      return this;
    }

    public @NotNull Builder setGeneratorBasedCoroutine() {
      isGeneratorBasedCoroutine = true;
      return this;
    }

    public @NotNull Builder setAbstract() {
      isAbstract = true;
      return this;
    }

    public @NotNull Builder setClassMethod() {
      isClassMethod = true;
      return this;
    }

    public @NotNull Builder setStaticMethod() {
      isStaticMethod = true;
      return this;
    }

    public @NotNull PyKnownDecorator build() {
      return new PyKnownDecorator(qualifiedName, isProperty, isMutableProperty, isGeneratorBasedCoroutine, isAbstract, isClassMethod, isStaticMethod);
    }
  }

  public @NotNull QualifiedName getQualifiedName() {
    return myQualifiedName;
  }

  public @NotNull String getShortName() {
    //noinspection ConstantConditions
    return myQualifiedName.getLastComponent();
  }

  public final static PyKnownDecorator STATICMETHOD = new PyKnownDecorator.Builder(PyNames.STATICMETHOD).setStaticMethod().build();
  public final static PyKnownDecorator CLASSMETHOD = new PyKnownDecorator.Builder(PyNames.CLASSMETHOD).setClassMethod().build();
  public final static PyKnownDecorator PROPERTY = new PyKnownDecorator.Builder(PyNames.PROPERTY).setProperty().build();

  public final static PyKnownDecorator FUNCTOOLS_LRU_CACHE = new PyKnownDecorator("functools.lru_cache");
  public final static PyKnownDecorator FUNCTOOLS_WRAPS = new PyKnownDecorator("functools.wraps");
  public final static PyKnownDecorator FUNCTOOLS_TOTAL_ORDERING = new PyKnownDecorator("functools.total_ordering");
  public final static PyKnownDecorator FUNCTOOLS_SINGLEDISPATCH = new PyKnownDecorator("functools.singledispatch");
  public final static PyKnownDecorator FUNCTOOLS_CACHED_PROPERTY = new PyKnownDecorator.Builder("functools.cached_property").setProperty().setMutableProperty().build();

  public final static PyKnownDecorator ABC_ABSTRACTMETHOD = new PyKnownDecorator.Builder("abc.abstractmethod").setAbstract().build();
  public final static PyKnownDecorator ABC_ABSTRACTCLASSMETHOD = new PyKnownDecorator.Builder("abc.abstractclassmethod").setAbstract().setClassMethod().build();
  public final static PyKnownDecorator ABC_ABSTRACTSTATICMETHOD = new PyKnownDecorator.Builder("abc.abstractstaticmethod").setAbstract().setStaticMethod().build();
  public final static PyKnownDecorator ABC_ABSTRACTPROPERTY = new PyKnownDecorator.Builder("abc.abstractproperty").setAbstract().setProperty().build();

  public final static PyKnownDecorator ASYNCIO_TASKS_COROUTINE = new PyKnownDecorator.Builder("asyncio.tasks.coroutine").setGeneratorBasedCoroutine().build();
  public final static PyKnownDecorator ASYNCIO_COROUTINES_COROUTINE = new PyKnownDecorator.Builder("asyncio.coroutines.coroutine").setGeneratorBasedCoroutine().build();
  public final static PyKnownDecorator TYPES_COROUTINE = new PyKnownDecorator.Builder("types.coroutine").setGeneratorBasedCoroutine().build();

  public final static PyKnownDecorator UNITTEST_SKIP = new PyKnownDecorator("unittest.case.skip");
  public final static PyKnownDecorator UNITTEST_SKIP_IF = new PyKnownDecorator("unittest.case.skipIf");
  public final static PyKnownDecorator UNITTEST_SKIP_UNLESS = new PyKnownDecorator("unittest.case.skipUnless");
  public final static PyKnownDecorator UNITTEST_EXPECTED_FAILURE = new PyKnownDecorator("unittest.case.expectedFailure");
  public final static PyKnownDecorator UNITTEST_MOCK_PATCH = new PyKnownDecorator("unittest.mock.patch");

  public final static PyKnownDecorator TYPING_OVERLOAD = new PyKnownDecorator("typing." + PyNames.OVERLOAD);
  public final static PyKnownDecorator TYPING_OVERRIDE = new PyKnownDecorator("typing." + PyNames.OVERRIDE);
  public final static PyKnownDecorator TYPING_EXTENSIONS_OVERRIDE = new PyKnownDecorator("typing_extensions." + PyNames.OVERRIDE);
  public final static PyKnownDecorator TYPING_RUNTIME = new PyKnownDecorator("typing.runtime");
  public final static PyKnownDecorator TYPING_RUNTIME_EXT = new PyKnownDecorator("typing_extensions.runtime");
  public final static PyKnownDecorator TYPING_RUNTIME_CHECKABLE = new PyKnownDecorator("typing.runtime_checkable");
  public final static PyKnownDecorator TYPING_RUNTIME_CHECKABLE_EXT = new PyKnownDecorator("typing_extensions.runtime_checkable");
  public final static PyKnownDecorator TYPING_FINAL = new PyKnownDecorator("typing.final");
  public final static PyKnownDecorator TYPING_FINAL_EXT = new PyKnownDecorator("typing_extensions.final");
  public final static PyKnownDecorator TYPING_DEPRECATED = new PyKnownDecorator("typing_extensions.deprecated");
  public final static PyKnownDecorator TYPING_NO_TYPE_CHECK = new PyKnownDecorator("typing.no_type_check");
  public final static PyKnownDecorator TYPING_NO_TYPE_CHECK_EXT = new PyKnownDecorator("typing_extensions.no_type_check");

  public final static PyKnownDecorator WARNING_DEPRECATED = new PyKnownDecorator("warnings.deprecated");

  public final static PyKnownDecorator REPRLIB_RECURSIVE_REPR = new PyKnownDecorator("reprlib.recursive_repr");

  public final static PyKnownDecorator PYRAMID_DECORATOR_REIFY = new PyKnownDecorator.Builder("pyramid.decorator.reify").setProperty().build();
  public final static PyKnownDecorator KOMBU_UTILS_CACHED_PROPERTY = new PyKnownDecorator.Builder("kombu.utils.cached_property").setProperty().setMutableProperty().build();

  public final static PyKnownDecorator DATACLASSES_DATACLASS = new PyKnownDecorator("dataclasses.dataclass");
  public final static PyKnownDecorator ATTR_S = new PyKnownDecorator("attr.s");
  public final static PyKnownDecorator ATTR_ATTRS = new PyKnownDecorator("attr.attrs");
  public final static PyKnownDecorator ATTR_ATTRIBUTES = new PyKnownDecorator("attr.attributes");
  public final static PyKnownDecorator ATTR_DATACLASS = new PyKnownDecorator("attr.dataclass");
  public final static PyKnownDecorator ATTR_DEFINE = new PyKnownDecorator("attr.define");
  public final static PyKnownDecorator ATTR_MUTABLE = new PyKnownDecorator("attr.mutable");
  public final static PyKnownDecorator ATTR_FROZEN = new PyKnownDecorator("attr.frozen");
  public final static PyKnownDecorator ATTRS_DEFINE = new PyKnownDecorator("attrs.define");
  public final static PyKnownDecorator ATTRS_MUTABLE = new PyKnownDecorator("attrs.mutable");
  public final static PyKnownDecorator ATTRS_FROZEN = new PyKnownDecorator("attrs.frozen");

  public final static PyKnownDecorator PYTEST_FIXTURES_FIXTURE = new PyKnownDecorator("_pytest.fixtures.fixture");
  public final static PyKnownDecorator ENUM_MEMBER = new PyKnownDecorator(PyNames.TYPE_ENUM_MEMBER);
  public final static PyKnownDecorator ENUM_NONMEMBER = new PyKnownDecorator(PyNames.TYPE_ENUM_NONMEMBER);

  /**
   * Mutable properties support __set__ and __delete__
   */
  public boolean isMutableProperty() {
    return myIsMutableProperty;
  }

  public boolean isProperty() {
    return myIsProperty;
  }

  public boolean isGeneratorBasedCoroutine() {
    return myIsGeneratorBasedCoroutine;
  }

  public boolean isAbstract() {
    return myIsAbstract;
  }

  public boolean isClassMethod() {
    return myIsClassMethod;
  }

  public boolean isStaticMethod() {
    return myIsStaticMethod;
  }
}
