package com.dianju.tranlate.controller;

import cn.com.webxml.TranslatorWebServiceSoap;
import org.junit.Test;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:applicationContext.xml")
public class TranlateTest {
    @Autowired
    private TranslatorWebServiceSoap   translatorWebServiceSoap;
    @Test
    public  void   a(){
        List<String>  list =  translatorWebServiceSoap.getEnCnTwoWayTranslator("hello").getString();
        System.out.println(list);
    }
}
