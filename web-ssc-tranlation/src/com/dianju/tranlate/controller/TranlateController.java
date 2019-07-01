package com.dianju.tranlate.controller;

import com.dianju.tranlate.service.TranlateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.UnsupportedEncodingException;

@Controller
public class TranlateController {

    @Autowired
    private TranlateService tranlateService;

    @RequestMapping("/tranlate")
    @ResponseBody
    public String tranlate(String source) {
        try {
            String str = new String(source.getBytes("ISO-8859-1"), "UTF-8");

            return tranlateService.tranlate(str);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
