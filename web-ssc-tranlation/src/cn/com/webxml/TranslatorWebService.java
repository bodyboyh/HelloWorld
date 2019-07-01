package cn.com.webxml;

import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.Service;

/**
 * <a href="http://www.webxml.com.cn/" target="_blank">WebXml.com.cn</a> <strong>中文<->英文双向翻译 WEB 服务</strong>，本词典库中大部分单词是由程序根据词频和英<->中单词间相互关联程度自动生成，难免存在有解释错误和牵强的地方请大家谅解。</br>此中文<->英文双向翻译Web Services请不要用于任何商业目的，若有需要请<a href="http://www.webxml.com.cn/zh_cn/contact_us.aspx" target="_blank">联系我们</a>，欢迎技术交流。 QQ：8409035<br /><strong>使用本站 WEB 服务请注明或链接本站：http://www.webxml.com.cn/ 感谢大家的支持</strong>！<br /><br />&nbsp;
 *
 * This class was generated by Apache CXF 3.2.9
 * 2019-06-30T23:06:34.853+08:00
 * Generated source version: 3.2.9
 *
 */
@WebServiceClient(name = "TranslatorWebService",
                  wsdlLocation = "http://www.webxml.com.cn/WebServices/TranslatorWebService.asmx?wsdl",
                  targetNamespace = "http://WebXml.com.cn/")
public class TranslatorWebService extends Service {

    public final static URL WSDL_LOCATION;

    public final static QName SERVICE = new QName("http://WebXml.com.cn/", "TranslatorWebService");
    public final static QName TranslatorWebServiceSoap12 = new QName("http://WebXml.com.cn/", "TranslatorWebServiceSoap12");
    public final static QName TranslatorWebServiceSoap = new QName("http://WebXml.com.cn/", "TranslatorWebServiceSoap");
    public final static QName TranslatorWebServiceHttpGet = new QName("http://WebXml.com.cn/", "TranslatorWebServiceHttpGet");
    public final static QName TranslatorWebServiceHttpPost = new QName("http://WebXml.com.cn/", "TranslatorWebServiceHttpPost");
    static {
        URL url = null;
        try {
            url = new URL("http://www.webxml.com.cn/WebServices/TranslatorWebService.asmx?wsdl");
        } catch (MalformedURLException e) {
            java.util.logging.Logger.getLogger(TranslatorWebService.class.getName())
                .log(java.util.logging.Level.INFO,
                     "Can not initialize the default wsdl from {0}", "http://www.webxml.com.cn/WebServices/TranslatorWebService.asmx?wsdl");
        }
        WSDL_LOCATION = url;
    }

    public TranslatorWebService(URL wsdlLocation) {
        super(wsdlLocation, SERVICE);
    }

    public TranslatorWebService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public TranslatorWebService() {
        super(WSDL_LOCATION, SERVICE);
    }

    public TranslatorWebService(WebServiceFeature ... features) {
        super(WSDL_LOCATION, SERVICE, features);
    }

    public TranslatorWebService(URL wsdlLocation, WebServiceFeature ... features) {
        super(wsdlLocation, SERVICE, features);
    }

    public TranslatorWebService(URL wsdlLocation, QName serviceName, WebServiceFeature ... features) {
        super(wsdlLocation, serviceName, features);
    }




    /**
     *
     * @return
     *     returns TranslatorWebServiceSoap
     */
    @WebEndpoint(name = "TranslatorWebServiceSoap12")
    public TranslatorWebServiceSoap getTranslatorWebServiceSoap12() {
        return super.getPort(TranslatorWebServiceSoap12, TranslatorWebServiceSoap.class);
    }

    /**
     *
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns TranslatorWebServiceSoap
     */
    @WebEndpoint(name = "TranslatorWebServiceSoap12")
    public TranslatorWebServiceSoap getTranslatorWebServiceSoap12(WebServiceFeature... features) {
        return super.getPort(TranslatorWebServiceSoap12, TranslatorWebServiceSoap.class, features);
    }


    /**
     *
     * @return
     *     returns TranslatorWebServiceSoap
     */
    @WebEndpoint(name = "TranslatorWebServiceSoap")
    public TranslatorWebServiceSoap getTranslatorWebServiceSoap() {
        return super.getPort(TranslatorWebServiceSoap, TranslatorWebServiceSoap.class);
    }

    /**
     *
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns TranslatorWebServiceSoap
     */
    @WebEndpoint(name = "TranslatorWebServiceSoap")
    public TranslatorWebServiceSoap getTranslatorWebServiceSoap(WebServiceFeature... features) {
        return super.getPort(TranslatorWebServiceSoap, TranslatorWebServiceSoap.class, features);
    }


    /**
     *
     * @return
     *     returns TranslatorWebServiceHttpGet
     */
    @WebEndpoint(name = "TranslatorWebServiceHttpGet")
    public TranslatorWebServiceHttpGet getTranslatorWebServiceHttpGet() {
        return super.getPort(TranslatorWebServiceHttpGet, TranslatorWebServiceHttpGet.class);
    }

    /**
     *
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns TranslatorWebServiceHttpGet
     */
    @WebEndpoint(name = "TranslatorWebServiceHttpGet")
    public TranslatorWebServiceHttpGet getTranslatorWebServiceHttpGet(WebServiceFeature... features) {
        return super.getPort(TranslatorWebServiceHttpGet, TranslatorWebServiceHttpGet.class, features);
    }


    /**
     *
     * @return
     *     returns TranslatorWebServiceHttpPost
     */
    @WebEndpoint(name = "TranslatorWebServiceHttpPost")
    public TranslatorWebServiceHttpPost getTranslatorWebServiceHttpPost() {
        return super.getPort(TranslatorWebServiceHttpPost, TranslatorWebServiceHttpPost.class);
    }

    /**
     *
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns TranslatorWebServiceHttpPost
     */
    @WebEndpoint(name = "TranslatorWebServiceHttpPost")
    public TranslatorWebServiceHttpPost getTranslatorWebServiceHttpPost(WebServiceFeature... features) {
        return super.getPort(TranslatorWebServiceHttpPost, TranslatorWebServiceHttpPost.class, features);
    }

}
