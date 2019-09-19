package cn.com.webxml;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;

/**
 * This class was generated by Apache CXF 3.2.9
 * 2019-06-30T23:06:34.797+08:00
 * Generated source version: 3.2.9
 *
 */
@WebService(targetNamespace = "http://WebXml.com.cn/", name = "TranslatorWebServiceSoap")
@XmlSeeAlso({ObjectFactory.class})
public interface TranslatorWebServiceSoap {

    /**
     * <br /><h3>获得中文<->英文双向翻译 String()</h3><p>输入参数：中文或英文单词；返回数据：一个一维字符串数组 String(1)，String(0) 中文为[拼音][国标码 部首 笔画 笔顺]，英文为[音标]；String(1) 译文 多个条目中间用 | 隔开，英文还包括单词属性</p><br />
     */
    @WebMethod(action = "http://WebXml.com.cn/getEnCnTwoWayTranslator")
    @RequestWrapper(localName = "getEnCnTwoWayTranslator", targetNamespace = "http://WebXml.com.cn/", className = "cn.com.webxml.GetEnCnTwoWayTranslator")
    @ResponseWrapper(localName = "getEnCnTwoWayTranslatorResponse", targetNamespace = "http://WebXml.com.cn/", className = "cn.com.webxml.GetEnCnTwoWayTranslatorResponse")
    @WebResult(name = "getEnCnTwoWayTranslatorResult", targetNamespace = "http://WebXml.com.cn/")
    public cn.com.webxml.ArrayOfString getEnCnTwoWayTranslator(
        @WebParam(name = "Word", targetNamespace = "http://WebXml.com.cn/")
        java.lang.String word
    );

    /**
     * <br /><h3>Hello! WebXml.com.cm</h3><br /><br />
     */
    @WebMethod(operationName = "HelloWebXml", action = "http://WebXml.com.cn/HelloWebXml")
    @RequestWrapper(localName = "HelloWebXml", targetNamespace = "http://WebXml.com.cn/", className = "cn.com.webxml.HelloWebXml")
    @ResponseWrapper(localName = "HelloWebXmlResponse", targetNamespace = "http://WebXml.com.cn/", className = "cn.com.webxml.HelloWebXmlResponse")
    @WebResult(name = "HelloWebXmlResult", targetNamespace = "http://WebXml.com.cn/")
    public java.lang.String helloWebXml();
}