package com.dianju.tranlate.service;

import cn.com.webxml.TranslatorWebServiceSoap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TranlateServiceImpl   implements   TranlateService {

    @Autowired
    private TranslatorWebServiceSoap translatorWebServiceSoap;
    @Override
    public String tranlate(String source) {
        String  str = translatorWebServiceSoap.getEnCnTwoWayTranslator(source).getString().toString();
        return str;
    }
}
