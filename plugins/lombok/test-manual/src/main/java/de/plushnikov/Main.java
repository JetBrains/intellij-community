package de.plushnikov;

import de.plushnikov.constructor.AllArgsConstructorClass;
import de.plushnikov.constructor.ReqArgsConstructorClass;
import de.plushnikov.data.DataClass;
import de.plushnikov.data.GenericPairClass;
import de.plushnikov.delegate.DelegateClass;
import de.plushnikov.getter.FieldGetter;
import de.plushnikov.getter.FieldGetterMethodExist;
import de.plushnikov.getter.FieldGetterWithAnnotations;
import de.plushnikov.log.CommonsLogClass;
import de.plushnikov.log.Log4jClass;
import de.plushnikov.log.LogClass;
import de.plushnikov.log.Slf4jClass;
import de.plushnikov.sub.SubBeanX;
import de.plushnikov.tostring.ClassToString;

import java.util.Date;

public class Main {

    public static void main(String[] args) {
        Bean bean = new Bean();
        bean.setName(100122);
        bean.setVorName("ssxsddsyss");
        bean.setBirthsday(new Date());
        bean.setName(22212);
        bean.getHasBCat();
        bean.isHasACat();

        System.out.println(bean.getName());
        System.out.println(bean.getVorName());
        System.out.println(bean.getBirthsday());
        System.out.println(bean.toString());

        SubBeanX subBean = new SubBeanX();
        subBean.setName(10120);
        subBean.setHasACat(true);
//        subBean.setVorNameB("sssdw");
//        subBean.setGeburtsdatum(new Date());
//        subBean.calcMe2(123);

        System.out.println(subBean.getName());
        System.out.println(subBean.getVorName());
        System.out.println(subBean.getGeburtsdatum2());
        System.out.println(subBean);

        FieldGetter clazz = new FieldGetter();

        clazz.getIntProperty();
        clazz.getPublicProperty();

        System.out.println(new ClassToString().toString());

        DataClass dataClass = new DataClass();
        System.out.println(dataClass.getIntProperty());
        System.out.println(dataClass.toString());

        AllArgsConstructorClass allArgsClass = AllArgsConstructorClass.of(12, 0.1f, "ddd");
        System.out.println(allArgsClass.hashCode());
        System.out.println(allArgsClass.toString());

        ReqArgsConstructorClass reqArgsClass = new ReqArgsConstructorClass(2.0f, "data data");
        System.out.println(reqArgsClass.hashCode());
        System.out.println(reqArgsClass.toString());

        LogClass logClass = new LogClass();
        logClass.doSomething();

        Log4jClass log4jClass = new Log4jClass();

        log4jClass.doSomething();

        CommonsLogClass commonsLogClass = new CommonsLogClass();
        commonsLogClass.doSomething();

        Slf4jClass slf4jLogClass = new Slf4jClass();
        slf4jLogClass.doSomething();

        DelegateClass delegate = new DelegateClass();
        delegate.add("String2");
        System.out.println(delegate.contains("String"));
        System.out.println(delegate.contains("String2"));

        FieldGetterMethodExist fieldGetterMethodExist = new FieldGetterMethodExist();
        fieldGetterMethodExist.getInt2Property();

        FieldGetterWithAnnotations a = new FieldGetterWithAnnotations();
        a.getInt2Integer();


        GenericPairClass<String, Integer> pairClass = new GenericPairClass<String, Integer>();
        pairClass.setKey("foowwwww");
        pairClass.setValue(123);
    }

}
