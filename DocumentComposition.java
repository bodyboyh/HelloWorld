package com.dianju.signatureServer;

import com.alibaba.fastjson.JSONObject;
import com.dianju.core.LicenseConfig;
import com.dianju.core.Util;
import com.dianju.core.ZipUtil;
import com.dianju.core.models.UUIDReduce;
import com.dianju.fapiao.DjFapiaoUtil;
import com.dianju.fapiao.po.*;
import com.dianju.modules.cert.models.Cert;
import com.dianju.modules.cert.models.CertDao;
import com.dianju.modules.document.models.DocumentDao;
import com.dianju.modules.gjzwInterface.DJException;
import com.dianju.modules.gjzwInterface.seal.GetSealStatusRequestData;
import com.dianju.modules.gjzwInterface.tool.SealUtilTool;
import com.dianju.modules.log.models.LogFileServerSeal;
import com.dianju.modules.log.models.LogFileServerSealDao;
import com.dianju.modules.log.models.LogServerSeal;
import com.dianju.modules.log.models.LogServerSealDao;
import com.dianju.modules.seal.models.SealDao;
import com.dianju.signatureServer.SignatureFileUploadAndDownLoad.Pattern;
import com.dianju.signatureServer.check.*;
import com.dianju.signatureServer.encryptionDevice.EncryptionDevice;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.Base64;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import srvSeal.SrvSealUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class DocumentComposition {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * 合成模式AddSeal盖章 NoSeal不盖章
     */
    public enum SyntheticPattern {
        AddSeal, NoSeal, AddEmptySeal
    }

    public enum OperationType {
        PdfVarify, GetInfor
    }

    /**
     * 文档合成接口
     *
     * @param xmlStr           请求报文
     * @param syntheticPattern 类型
     * @param beginTime        开始时间
     * @param request
     * @return 响应报文
     */
    public String sealAutoPdf(String xmlStr, SyntheticPattern syntheticPattern, long beginTime, HttpServletRequest request) {
        try {
            //创建所有服务端签章需要的文件夹(如果没有则创建)
            initDir();
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            SealAutoPdfCheck check = new SealAutoPdfCheck(beginTime + "", syntheticPattern);
            LogServerSeal logServerSeal = new LogServerSeal();
            logServerSeal.setRequestXml(xmlStr);
            // logServerSeal.setCreatedAt(Util.getTimeStampOfNow());
            logServerSealDao.save(logServerSeal);
            String returnXml = null;
            //XML格式与内容判断
            if (!check.sealAutoPdf(sealDocRequest, request)) {
                log.info(beginTime + ":xml格式判断:" + check.getError());
                logServerSeal.setResult(0);
                returnXml = getReturnXml(null, "", beginTime, syntheticPattern, check.getError());
            } else {
                log.info(beginTime + ":xml格式判断:成功");
                logServerSeal.setResult(1);

                documentInfo.put("sysId", check.params.get("SYS_ID"));//存放系统id
                documentInfo.put("sourceType", "3");//服务端合成
                if (SyntheticPattern.AddSeal == syntheticPattern) {//签章
                    documentInfo.put("sourceType", "2");
                }

                Element META_DATA = sealDocRequest.element("META_DATA");
                String IS_MERGER = META_DATA.elementText("IS_MERGER");
                Map sealAutoPdfForMerageRet = new HashMap();
                if ("true".equals(IS_MERGER)) {
                    sealAutoPdfForMerageRet = sealAutoPdfForMerage(sealDocRequest, syntheticPattern, beginTime + "");
                    returnXml = getReturnXml(sealAutoPdfForMerageRet, "sealFilePath", beginTime, syntheticPattern);
                } else {
                    sealAutoPdfForMerageRet = sealAutoPdfForNotMerge(sealDocRequest, syntheticPattern, beginTime + "");
                    returnXml = getReturnXml(sealAutoPdfForMerageRet, "sealFilePath", beginTime, syntheticPattern);
                }


                //记录日志
                Map<String, String> map = check.params;
                LogFileServerSeal logFileServerSeal = new LogFileServerSeal();
                logFileServerSeal.setSystemId(map.get("SYS_ID"));
                logFileServerSeal.setIpAddress(request.getRemoteAddr());
                logFileServerSeal.setLogServerSealId(logServerSeal.getId());
                logFileServerSeal.setDescription("");
                Iterator iterator = sealAutoPdfForMerageRet.values().iterator();
                while (iterator.hasNext()) {
                    Map m = (Map) iterator.next();
                    //logFileServerSeal.setId(null);
                    logFileServerSeal.setResult(Integer.parseInt(m.get("RET_CODE") + ""));
                    logFileServerSeal.setDocumentName((String) m.get("FILE_NO"));
                    // logFileServerSeal.setCreatedAt(Util.getTimeStampOfNow());
                    logFileServerSealDao.save(logFileServerSeal);
                }
            }
            logServerSeal.setResponseXml(returnXml);
            logServerSealDao.save(logServerSeal);

            return returnXml;
        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getReturnXml(null, "", beginTime, syntheticPattern, "xml解析失败");
        } catch (Exception e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getReturnXml(null, "", beginTime, syntheticPattern, "模版合成与盖章失败");
        }
    }

    public String sealAutoAip(String xmlStr, SyntheticPattern syntheticPattern, long beginTime, HttpServletRequest request) {
        try {
            //创建所有服务端签章需要的文件夹(如果没有则创建)
            initDir();
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            SealAutoPdfCheck check = new SealAutoPdfCheck(beginTime + "", syntheticPattern);
            LogServerSeal logServerSeal = new LogServerSeal();
            logServerSeal.setRequestXml(xmlStr);
            // logServerSeal.setCreatedAt(Util.getTimeStampOfNow());
            logServerSealDao.save(logServerSeal);
            String returnXml = null;
            //XML格式与内容判断
            if (!check.sealAutoPdf(sealDocRequest, request)) {
                log.info(beginTime + ":xml格式判断:" + check.getError());
                logServerSeal.setResult(0);
                returnXml = getReturnXml(null, "", beginTime, syntheticPattern, check.getError());
            } else {
                log.info(beginTime + ":xml格式判断:成功");
                logServerSeal.setResult(1);

                documentInfo.put("sysId", check.params.get("SYS_ID"));//存放系统id
                documentInfo.put("sourceType", "3");//服务端合成
                if (SyntheticPattern.AddSeal == syntheticPattern) {//签章
                    documentInfo.put("sourceType", "2");
                }

                Element META_DATA = sealDocRequest.element("META_DATA");
                String IS_MERGER = META_DATA.elementText("IS_MERGER");
                Map sealAutoAipForMerageRet = new HashMap();
                if ("true".equals(IS_MERGER)) {
                    sealAutoAipForMerageRet = sealAutoAipForMerage(sealDocRequest, syntheticPattern, beginTime + "");
                    returnXml = getReturnXml(sealAutoAipForMerageRet, "sealFilePath", beginTime, syntheticPattern);
                } else {
                    sealAutoAipForMerageRet = sealAutoAipForNotMerge(sealDocRequest, syntheticPattern, beginTime + "");
                    returnXml = getReturnXml(sealAutoAipForMerageRet, "sealFilePath", beginTime, syntheticPattern);
                }


                //记录日志
                Map<String, String> map = check.params;
                LogFileServerSeal logFileServerSeal = new LogFileServerSeal();
                logFileServerSeal.setSystemId(map.get("SYS_ID"));
                logFileServerSeal.setIpAddress(request.getRemoteAddr());
                logFileServerSeal.setLogServerSealId(logServerSeal.getId());
                logFileServerSeal.setDescription("");
                Iterator iterator = sealAutoAipForMerageRet.values().iterator();
                while (iterator.hasNext()) {
                    Map m = (Map) iterator.next();
                    //logFileServerSeal.setId(null);
                    logFileServerSeal.setResult(Integer.parseInt(m.get("RET_CODE") + ""));
                    logFileServerSeal.setDocumentName((String) m.get("FILE_NO"));
                    // logFileServerSeal.setCreatedAt(Util.getTimeStampOfNow());
                    logFileServerSealDao.save(logFileServerSeal);
                }
            }
            logServerSeal.setResponseXml(returnXml);
            logServerSealDao.save(logServerSeal);

            return returnXml;
        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getReturnXml(null, "", beginTime, syntheticPattern, "xml解析失败");
        } catch (Exception e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getReturnXml(null, "", beginTime, syntheticPattern, "模版合成与盖章失败");
        }
    }

    public void init() {
        if (srvSealUtil == null) {
            srvSealUtil = (SrvSealUtil) Util.getBean("srvSealUtil");
        }
        if (syntheticType == null) {
//            path = Util.getSystemDictionary("upload_path");
//            filePath = path + "/filePath";
//            sealFilePath = path + "/sealFilePath";
            syntheticType = Util.getSystemDictionary("synthetic_type");

            path = Util.getSystemDictionary("upload_path");
            filePath = path + "/" + FILE_PATH + "/";
            sealFilePath = path + "/" + SEAL_FILE_PATH + "/";
            //       syntheticType = Util.getSystemDictionary("synthetic_type");
            htmlPath = path + "/htmlPath/";
            htmltoPdfPath = path + "/htmltoPdfPath/";
            pdfToImg = path + "/pdfToImg/";
            fileToPicture = path + "/fileToPicture/";
        }
    }

    /**
     * 文档合成（合并）
     *
     * @param sealDocRequest
     * @param syntheticPattern
     * @param beginTime
     * @return
     */
    private Map sealAutoPdfForMerage(Element sealDocRequest, SyntheticPattern syntheticPattern, String beginTime) throws Exception {
        Map retMap = new HashMap();
        Element META_DATA = sealDocRequest.element("META_DATA");
        retMap.put("FILE_NO", META_DATA.elementText("FILE_NO"));
        // try {
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map downFileMap = new HashMap<Integer, String>();
        String fileDownRet = SignatureFileUploadAndDownLoad.downFile(TREE_NODES, beginTime, downFileMap, Pattern.End);
        if (!"ok".equals(fileDownRet)) {
            throw new Exception(fileDownRet);
        }

        init();//初始化控件
        String savePath = filePath + "/" + beginTime + "." + syntheticType;
        int nObjID = documentCreating.openObj("", 0, 0);
        log.info(beginTime + ":nObjID:" + nObjID);
        try {
            if (nObjID <= 0) {
                log.info(beginTime + ":服务器繁忙，请稍后重试1");
                throw new Exception("服务器繁忙，请稍后重试1");
            }
            int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
            log.info(beginTime + ":login:" + l);
            if (l != 0) {
                throw new Exception("未授权的服务器");
            }
            String makeMergerFileret = documentCreating.makeMergerFile(nObjID, FILE_LIST, downFileMap, beginTime);
            if (!"ok".equals(makeMergerFileret)) {
                retMap.put("FILE_MSG", makeMergerFileret);
                throw new Exception(makeMergerFileret);
            }
            String insertCodeBarret = documentCreating.insertCodeBar(nObjID, META_DATA, beginTime);
            if (!"ok".equals(insertCodeBarret)) {
                throw new Exception(insertCodeBarret);
            }
            int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 1);
            log.info(beginTime + ":saveFile:" + saveFileRet);
            if (saveFileRet == 0) {
                log.info(beginTime + ":saveFile文档保存失败，请检查服务器，保存路径：" + savePath);
                throw new Exception("saveFile文档保存失败");
            } else {
                log.info(beginTime + ":saveFile文档保存成功" + new Date() + "保存路径：" + savePath);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            log.info("saveFile文档关闭");
            documentCreating.saveFile(nObjID, "", syntheticType, 0);
        }

          /*  nObjID = documentCreating.openObj(savePath, 0, 0);
            log.info(beginTime + ":nObjID:" + nObjID);
            if (nObjID <= 0) {
                log.info(beginTime + ":服务器繁忙，请稍后重试4");
                throw new Exception("服务器繁忙，请稍后重试4");
            }*/
        try {
            //  int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
            //  log.info(beginTime + ":login:" + l);
            String addSealret = documentCreating.addSeal(savePath, syntheticPattern, META_DATA, beginTime);
            if (!"ok".equals(addSealret)) {
                throw new Exception(addSealret);
            } else {

                String creator = documentInfo.get("sysId");
                String creatorName = documentInfo.get("sysId");
                byte sourceType = Byte.parseByte(documentInfo.get("sourceType"));
                String filepath = sealFilePath + "/" + retMap.get("FILE_NO");
                //文件信息保存
                boolean saveInfo = this.saveServerDocument(retMap.get("FILE_NO") + "", creator, creatorName, filepath, sourceType);
                if (!saveInfo) {
                    log.info("向document表汇中插入文档信息失败");
                    throw new Exception("向document表汇中插入文档信息失败");
                }

                if (META_DATA.elementText("FTP_SAVEPATH") != null && !"".equals(META_DATA.elementText("FTP_SAVEPATH"))) {
                    String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(META_DATA, sealFilePath + "/" + META_DATA.elementText("FILE_NO"), beginTime);
                    if ("ok".equals(ftpUpFileRet)) {
                        retMap.put("RET_CODE", "1");
                        retMap.put("FILE_MSG", "文档上传成功");
                    } else {
                        retMap.put("RET_CODE", "0");
                        retMap.put("FILE_MSG", ftpUpFileRet);
                    }

                } else {
                    retMap.put("RET_CODE", "1");
                    retMap.put("FILE_MSG", "文档合成成功");
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            retMap.put("RET_CODE", "0");
            retMap.put("FILE_MSG", e.getMessage());
        }
        Map m = new HashMap();
        m.put(0, retMap);
        return m;
    }

    private Map sealAutoAipForMerage(Element sealDocRequest, SyntheticPattern syntheticPattern, String beginTime) throws Exception {
        Map retMap = new HashMap();
        Element META_DATA = sealDocRequest.element("META_DATA");
        retMap.put("FILE_NO", META_DATA.elementText("FILE_NO"));
        // try {
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map downFileMap = new HashMap<Integer, String>();
        String fileDownRet = SignatureFileUploadAndDownLoad.downFile(TREE_NODES, beginTime, downFileMap, Pattern.End);
        if (!"ok".equals(fileDownRet)) {
            throw new Exception(fileDownRet);
        }

        init();//初始化控件
        String savePath = filePath + "/" + beginTime + "." + syntheticType;
        int nObjID = documentCreating.openObj("", 0, 0);
        log.info(beginTime + ":nObjID:" + nObjID);
        try {
            if (nObjID <= 0) {
                log.info(beginTime + ":服务器繁忙，请稍后重试1");
                throw new Exception("服务器繁忙，请稍后重试1");
            }
            int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
            log.info(beginTime + ":login:" + l);
            if (l != 0) {
                throw new Exception("未授权的服务器");
            }
            String makeMergerFileret = documentCreating.makeMergerFile(nObjID, FILE_LIST, downFileMap, beginTime);
            if (!"ok".equals(makeMergerFileret)) {
                retMap.put("FILE_MSG", makeMergerFileret);
                throw new Exception(makeMergerFileret);
            }
            String insertCodeBarret = documentCreating.insertCodeBar(nObjID, META_DATA, beginTime);
            if (!"ok".equals(insertCodeBarret)) {
                throw new Exception(insertCodeBarret);
            }
            int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 1);
            log.info(beginTime + ":saveFile:" + saveFileRet);
            if (saveFileRet == 0) {
                log.info(beginTime + ":saveFile文档保存失败，请检查服务器，保存路径：" + savePath);
                throw new Exception("saveFile文档保存失败");
            } else {
                log.info(beginTime + ":saveFile文档保存成功" + new Date() + "保存路径：" + savePath);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            log.info("saveFile文档关闭");
            documentCreating.saveFile(nObjID, "", syntheticType, 0);
        }

          /*  nObjID = documentCreating.openObj(savePath, 0, 0);
            log.info(beginTime + ":nObjID:" + nObjID);
            if (nObjID <= 0) {
                log.info(beginTime + ":服务器繁忙，请稍后重试4");
                throw new Exception("服务器繁忙，请稍后重试4");
            }*/
        try {
            //  int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
            //  log.info(beginTime + ":login:" + l);
            String addSealret = documentCreating.addSeal(savePath, syntheticPattern, META_DATA, beginTime);
            if (!"ok".equals(addSealret)) {
                throw new Exception(addSealret);
            } else {

                String creator = documentInfo.get("sysId");
                String creatorName = documentInfo.get("sysId");
                byte sourceType = Byte.parseByte(documentInfo.get("sourceType"));
                String filepath = sealFilePath + "/" + retMap.get("FILE_NO");
                //文件信息保存
                boolean saveInfo = this.saveServerDocument(retMap.get("FILE_NO") + "", creator, creatorName, filepath, sourceType);
                if (!saveInfo) {
                    log.info("向document表汇中插入文档信息失败");
                    throw new Exception("向document表汇中插入文档信息失败");
                }

                if (META_DATA.elementText("FTP_SAVEPATH") != null && !"".equals(META_DATA.elementText("FTP_SAVEPATH"))) {
                    String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(META_DATA, sealFilePath + "/" + META_DATA.elementText("FILE_NO"), beginTime);
                    if ("ok".equals(ftpUpFileRet)) {
                        retMap.put("RET_CODE", "1");
                        retMap.put("FILE_MSG", "文档上传成功");
                    } else {
                        retMap.put("RET_CODE", "0");
                        retMap.put("FILE_MSG", ftpUpFileRet);
                    }

                } else {
                    retMap.put("RET_CODE", "1");
                    retMap.put("FILE_MSG", "文档合成成功");
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
            retMap.put("RET_CODE", "0");
            retMap.put("FILE_MSG", e.getMessage());
        }
        Map m = new HashMap();
        m.put(0, retMap);
        return m;
    }

    /**
     * 文档合成不合并
     *
     * @param sealDocRequest
     * @param syntheticPattern
     * @param beginTime
     * @return
     */
    private Map sealAutoPdfForNotMerge(Element sealDocRequest, SyntheticPattern syntheticPattern, String beginTime) throws Exception {
        init();//初始化控件
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map msgMap = new HashMap<Integer, Map<String, String>>();
        SignatureFileUploadAndDownLoad.downFile(TREE_NODES, beginTime, msgMap, Pattern.Next);
        documentCreating.templateSynthesis(TREE_NODES, beginTime + "", msgMap);
        for (int i = 0; i < TREE_NODES.size(); i++) {
            log.info("进入循环---------");
            Map thisMsg = (Map) msgMap.get(i);
            Element TREE_NODE = TREE_NODES.get(i);
            thisMsg.put("FILE_NO", TREE_NODE.elementText("FILE_NO"));
            if ("1".equals(thisMsg.get("RET_CODE") + "")) {
                log.info("进入循环2------");
                String filePath = (String) thisMsg.get("FILE_MSG");
                //对filepath进行处理，非pdf文件转换为pdf文件
                int len = filePath.lastIndexOf(".");
                String fileSuffix = filePath.substring(len);
                String newFilePath = filePath.substring(0, len) + ".pdf";
                log.info("日志1---------");
                if (fileSuffix.equals(".doc") || fileSuffix.equals(".docx") || fileSuffix.equals(".xls") || fileSuffix.equals(".xlsx") || fileSuffix.equals(".ppt") || fileSuffix.equals(".pptx")) {
                    log.info("日志2-------");
                    int otp = srvSealUtil.officeToPdf(-1, filePath, newFilePath);
                    log.info("日志3--------");
                    log.info("opt：" + otp);
                    System.out.println("转化文档(1为成功)：" + otp);
                    if (otp < 1) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (fileSuffix.equals(".aip") || fileSuffix.equals(".txt") || fileSuffix.equals(".bmp")) {
                    int nObjID = srvSealUtil.openObj(filePath, 0, 0);
                    System.out.println("nObjID：" + nObjID);
                    if (nObjID <= 0) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    int l = srvSealUtil.login(nObjID, 4, "HWSEALDEMOXX", "DEMO");
                    System.out.println("login:" + l);
                    if (l != 0) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    log.info("newFilePath1:" + newFilePath);
                    int save = srvSealUtil.saveFile(nObjID, newFilePath, "pdf", 0);
                    log.info("打开newFilePath返回值：" + save);
                    System.out.println("save(1为成功)：" + save);
                    if (save != 1) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                String fileNo = (String) thisMsg.get("FILE_NO");
                String savePath = this.filePath + "/" + fileNo;

                log.info("newFilePath2:" + newFilePath);
                int nObjID = documentCreating.openObj(newFilePath, 0, 0);
                int ret = 1;
                log.info(beginTime + ":nObjID:" + nObjID);
                if (nObjID <= 0) {
                    log.info(beginTime + ":服务器繁忙，请稍后重试3");
                    thisMsg.put("FILE_MSG", "服务器繁忙，请稍后重试3");
                    ret = 0;
                }
                int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
                log.info(fileNo + ":login:" + l);
                try {
                    String insertCodeBarret = documentCreating.insertCodeBar(nObjID, TREE_NODE, fileNo);
                    if (!"ok".equals(insertCodeBarret)) {
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", "添加二维码失败");
                        ret = 0;
                    }

                    int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 1);
                    if (saveFileRet == 0) {
                        log.info(beginTime + ":saveFile文档保存失败，请检查服务器，保存路径：" + savePath);
                        throw new Exception("saveFile文档保存失败");
                    } else {
                        log.info(beginTime + ":saveFile文档保存成功" + new Date() + "保存路径：" + savePath);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    log.info(fileNo + ":" + i + "文档关闭");
                    documentCreating.saveFile(nObjID, "", syntheticType, 0);
                }
                if (ret == 1) {
                    String addSealret = documentCreating.addSeal(savePath, syntheticPattern, TREE_NODE, fileNo);
                    if (!"ok".equals(addSealret)) {
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", addSealret);
                        ret = 0;
                    } else {

                        String creator = documentInfo.get("sysId");
                        String creatorName = documentInfo.get("sysId");
                        byte sourceType = Byte.parseByte(documentInfo.get("sourceType"));
                        String filepath = sealFilePath + "/" + fileNo;
                        //文件信息保存
                        boolean saveInfo = this.saveServerDocument(fileNo, creator, creatorName, filepath, sourceType);
                        if (!saveInfo) {
                            log.info("向document表汇中插入文档信息失败");
                            throw new Exception("向document表汇中插入文档信息失败");
                        }

                        if (TREE_NODE.elementText("FTP_SAVEPATH") != null && !"".equals(TREE_NODE.elementText("FTP_SAVEPATH"))) {
                            String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(TREE_NODE, sealFilePath + "/" + TREE_NODE.elementText("FILE_NO"), beginTime);
                            if ("ok".equals(ftpUpFileRet)) {
                                thisMsg.put("RET_CODE", "1");
                                thisMsg.put("FILE_MSG", "文档上传成功");
                            } else {
                                thisMsg.put("RET_CODE", "0");
                                thisMsg.put("FILE_MSG", ftpUpFileRet);
                            }

                        } else {
                            thisMsg.put("RET_CODE", "1");
                            thisMsg.put("FILE_MSG", "文档合成成功");
                        }
                    }
                }


            }

        }
        log.info("走完了");
        return msgMap;
    }

    /**
     * 文档合成不合并
     *
     * @param sealDocRequest
     * @param syntheticPattern
     * @param beginTime
     * @return
     */
    private Map sealAutoAipForNotMerge(Element sealDocRequest, SyntheticPattern syntheticPattern, String beginTime) throws Exception {
        init();//初始化控件
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map msgMap = new HashMap<Integer, Map<String, String>>();
        SignatureFileUploadAndDownLoad.downFile(TREE_NODES, beginTime, msgMap, Pattern.Next);
        documentCreating.templateSynthesis(TREE_NODES, beginTime + "", msgMap);
        for (int i = 0; i < TREE_NODES.size(); i++) {
            log.info("进入循环--------");
            Map thisMsg = (Map) msgMap.get(i);
            Element TREE_NODE = TREE_NODES.get(i);
            thisMsg.put("FILE_NO", TREE_NODE.elementText("FILE_NO"));
            if ("1".equals(thisMsg.get("RET_CODE") + "")) {

                String filePath = (String) thisMsg.get("FILE_MSG");
                //对filepath进行处理，非pdf文件转换为pdf文件
                int len = filePath.lastIndexOf(".");
                String fileSuffix = filePath.substring(len);
                String newFilePath = filePath.substring(0, len) + ".aip";

                if (fileSuffix.equals(".doc") || fileSuffix.equals(".docx") || fileSuffix.equals(".xls") || fileSuffix.equals(".xlsx") || fileSuffix.equals(".ppt") || fileSuffix.equals(".pptx")) {
                    System.out.println("openObj打开之前----------");
                    log.info("openObj打开之前----------");
                    int nObjID = srvSealUtil.openObj(filePath, 0, 0);
                    System.out.println("openObj打开之后----------");
                    log.info("openObj打开之后----------");
                    System.out.println("nObjID：" + nObjID);
                    if (nObjID <= 0) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    int l = srvSealUtil.login(nObjID, 4, "HWSEALDEMOXX", "DEMO");
                    System.out.println("login:" + l);
                    if (l != 0) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    int save = srvSealUtil.saveFile(nObjID, newFilePath, "aip", 0);
                    System.out.println("save(1为成功)：" + save);
                    if (save != 1) {
                        try {
                            throw new Exception("文件转换异常");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                String fileNo = (String) thisMsg.get("FILE_NO");
                String savePath = this.filePath + "/" + fileNo;
                int nObjID = documentCreating.openObj(newFilePath, 0, 0);
                int ret = 1;
                log.info(beginTime + ":nObjID:" + nObjID);
                if (nObjID <= 0) {
                    log.info(beginTime + ":服务器繁忙，请稍后重试3");
                    thisMsg.put("FILE_MSG", "服务器繁忙，请稍后重试3");
                    ret = 0;
                }
                int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
                log.info(fileNo + ":login:" + l);
                try {
                    String insertCodeBarret = documentCreating.insertCodeBar(nObjID, TREE_NODE, fileNo);
                    if (!"ok".equals(insertCodeBarret)) {
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", "添加二维码失败");
                        ret = 0;
                    }

                    int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 1);
                    if (saveFileRet == 0) {
                        log.info(beginTime + ":saveFile文档保存失败，请检查服务器，保存路径：" + savePath);
                        throw new Exception("saveFile文档保存失败");
                    } else {
                        log.info(beginTime + ":saveFile文档保存成功" + new Date() + "保存路径：" + savePath);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    log.info(fileNo + ":" + i + "文档关闭");
                    documentCreating.saveFile(nObjID, "", syntheticType, 0);
                }
                if (ret == 1) {
                    String addSealret = documentCreating.addSeal(savePath, syntheticPattern, TREE_NODE, fileNo);
                    if (!"ok".equals(addSealret)) {
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", addSealret);
                        ret = 0;
                    } else {

                        String creator = documentInfo.get("sysId");
                        String creatorName = documentInfo.get("sysId");
                        byte sourceType = Byte.parseByte(documentInfo.get("sourceType"));
                        String filepath = sealFilePath + "/" + fileNo;
                        //文件信息保存
                        boolean saveInfo = this.saveServerDocument(fileNo, creator, creatorName, filepath, sourceType);
                        if (!saveInfo) {
                            log.info("向document表汇中插入文档信息失败");
                            throw new Exception("向document表汇中插入文档信息失败");
                        }

                        if (TREE_NODE.elementText("FTP_SAVEPATH") != null && !"".equals(TREE_NODE.elementText("FTP_SAVEPATH"))) {
                            String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(TREE_NODE, sealFilePath + "/" + TREE_NODE.elementText("FILE_NO"), beginTime);
                            if ("ok".equals(ftpUpFileRet)) {
                                thisMsg.put("RET_CODE", "1");
                                thisMsg.put("FILE_MSG", "文档上传成功");
                            } else {
                                thisMsg.put("RET_CODE", "0");
                                thisMsg.put("FILE_MSG", ftpUpFileRet);
                            }

                        } else {
                            thisMsg.put("RET_CODE", "1");
                            thisMsg.put("FILE_MSG", "文档合成成功");
                        }
                    }
                }


            }

        }
        return msgMap;
    }



  /*  private Map sealAutoPdfForNotMerge(Element sealDocRequest, SyntheticPattern syntheticPattern, String beginTime) throws Exception {
        init();//初始化控件
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map msgMap = new HashMap<Integer, Map<String, String>>();
        SignatureFileUploadAndDownLoad.downFile(TREE_NODES, beginTime, msgMap, Pattern.Next);
        documentCreating.templateSynthesis(TREE_NODES, beginTime + "", msgMap);
        for (int i = 0; i < TREE_NODES.size(); i++) {
        	log.info("进入循环--------");
            Map thisMsg = (Map) msgMap.get(i);
            Element TREE_NODE = TREE_NODES.get(i);
            thisMsg.put("FILE_NO", TREE_NODE.elementText("FILE_NO"));
            if ("1".equals(thisMsg.get("RET_CODE") + "")) {

                String filePath = (String) thisMsg.get("FILE_MSG");
                //对filepath进行处理，非pdf文件转换为pdf文件
                int len = filePath.lastIndexOf(".");
                String fileSuffix = filePath.substring(len);
                String newFilePath = filePath.substring(0, len)+".pdf";

            	if (fileSuffix.equals(".doc") || fileSuffix.equals(".docx") || fileSuffix.equals(".xls")|| fileSuffix.equals(".xlsx") || fileSuffix.equals(".ppt") || fileSuffix.equals(".pptx")) {
            		System.out.println("openObj打开之前----------");
            		log.info("openObj打开之前----------");
            		int nObjID = srvSealUtil.openObj(filePath, 0, 0);
            		System.out.println("openObj打开之后----------");
            		log.info("openObj打开之后----------");
            		log.info("nObjID:"+nObjID);
            		log.info("filePath:"+filePath);
					if(nObjID<=0){
						try {
							throw new Exception("文件转换异常");
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					int l = srvSealUtil.login(nObjID, 4, "HWSEALDEMOXX","DEMO");
					System.out.println("login:" + l);
					if(l!=0){
						try {
							throw new Exception("文件转换异常");
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					int save = srvSealUtil.saveFile(nObjID, newFilePath, "pdf",0);
					System.out.println("save(1为成功)：" + save);
					if(save!=1){
						try {
							throw new Exception("文件转换异常");
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}

                String fileNo = (String) thisMsg.get("FILE_NO");
                String savePath = this.filePath + "/" + fileNo ;
                int nObjID = documentCreating.openObj(newFilePath, 0, 0);
                int ret = 1;
                log.info(beginTime + ":nObjID:" + nObjID);
                if (nObjID <= 0) {
                    log.info(beginTime + ":服务器繁忙，请稍后重试3");
                    thisMsg.put("FILE_MSG", "服务器繁忙，请稍后重试3");
                    ret = 0;
                }
                int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
                log.info(fileNo + ":login:" + l);
                try {
                    String insertCodeBarret = documentCreating.insertCodeBar(nObjID, TREE_NODE, fileNo);
                    if (!"ok".equals(insertCodeBarret)) {
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", "添加二维码失败");
                        ret = 0;
                    }

                    int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 1);
                    if (saveFileRet == 0) {
                        log.info(beginTime + ":saveFile文档保存失败，请检查服务器，保存路径：" + savePath);
                        throw new Exception("saveFile文档保存失败");
                    } else {
                        log.info(beginTime + ":saveFile文档保存成功" + new Date() + "保存路径：" + savePath);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                } finally {
                    log.info(fileNo + ":" + i + "文档关闭");
                    documentCreating.saveFile(nObjID, "", syntheticType, 0);
                }
                if (ret == 1) {
                    String addSealret = documentCreating.addSeal(savePath, syntheticPattern, TREE_NODE, fileNo);
                    if (!"ok".equals(addSealret)) {
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", addSealret);
                        ret = 0;
                    } else {

                        String creator = documentInfo.get("sysId");
                        String creatorName = documentInfo.get("sysId");
                        byte sourceType = Byte.parseByte(documentInfo.get("sourceType"));
                        String filepath = sealFilePath +"/"+fileNo;
                        //文件信息保存
                        boolean saveInfo = this.saveServerDocument(fileNo,creator,creatorName,filepath,sourceType);
                        if (!saveInfo){
                            log.info("向document表汇中插入文档信息失败");
                            throw new Exception("向document表汇中插入文档信息失败");
                        }

                        if (TREE_NODE.elementText("FTP_SAVEPATH") != null && !"".equals(TREE_NODE.elementText("FTP_SAVEPATH"))) {
                            String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(TREE_NODE, sealFilePath + "/" + TREE_NODE.elementText("FILE_NO"), beginTime);
                            if ("ok".equals(ftpUpFileRet)) {
                                thisMsg.put("RET_CODE", "1");
                                thisMsg.put("FILE_MSG", "文档上传成功");
                            } else {
                                thisMsg.put("RET_CODE", "0");
                                thisMsg.put("FILE_MSG", ftpUpFileRet);
                            }

                        } else {
                            thisMsg.put("RET_CODE", "1");
                            thisMsg.put("FILE_MSG", "文档合成成功");
                        }
                    }
                }


            }

        }
        return msgMap;
    }*/

    /**
     * 文档验证接口
     *
     * @param xmlStr    请求报文
     * @param beginTime 开始时间
     * @param request
     * @return 响应报文
     */
    public String pdfVarify(String xmlStr, String beginTime, HttpServletRequest request) {
        try {
            Document doc = DocumentHelper.parseText(xmlStr);
            Element verifyDocRequest = doc.getRootElement();
            PdfVarifyCheck check = new PdfVarifyCheck(beginTime);
            boolean result = check.pdfVarify(verifyDocRequest, request);
            String FILE_PATH = null;
            String FILE_NO = null;
            String FILE_TYPE = null;
            String FTP_ADDRESS = null;
            String FTP_PORT = null;
            String FTP_USER = null;
            String FTP_PWD = null;
            if (!result) {
                log.info("xml格式判断:" + check.getError());
                try {
                    return getPdfVarifyReturnXml(verifyDocRequest.element("META_DATA").elementText("FILE_NO"), check.getError(), "0", beginTime);
                } catch (Exception e) {
                    return getPdfVarifyReturnXml(null, check.getError(), "0", beginTime);
                }
            } else {
                Element META_DATA = verifyDocRequest.element("META_DATA");
                FILE_PATH = META_DATA.elementText("FILE_PATH");
                FILE_NO = META_DATA.elementText("FILE_NO");
                FILE_TYPE = META_DATA.elementText("FILE_TYPE");
                if (FILE_TYPE.equals("1")) {
                    FTP_ADDRESS = META_DATA.elementText("FTP_ADDRESS");
                    FTP_PORT = META_DATA.elementText("FTP_PORT");
                    FTP_USER = META_DATA.elementText("FTP_USER");
                    FTP_PWD = META_DATA.elementText("FTP_PWD");
                }
                log.info("xml格式判断:成功");
            }
            Map filePaths = new HashMap();
            String FileDownRet = null;
            String ftpEncoding = null;
            if (FILE_TYPE.equals("1")) {
                FileDownRet = SignatureFileUploadAndDownLoad.ftpDownFile1(ftpEncoding, FTP_ADDRESS, FTP_PORT, FTP_USER, FTP_PWD, FILE_PATH, beginTime, filePaths);
            } else {
                FileDownRet = SignatureFileUploadAndDownLoad.httpDownFile(FILE_PATH, beginTime, filePaths, null);
            }
            if (!"ok".equals(FileDownRet)) {
                return getPdfVarifyReturnXml(FILE_NO, FileDownRet, "0", beginTime);
            }
            init();//初始化控件
            DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
            int nObjID = documentCreating.openObj((String) filePaths.get("fileUrl"), 0, 0);
            log.info(beginTime + ":nObjID:" + nObjID);
            try {
                if (nObjID <= 0) {
                    log.info(beginTime + ":服务器繁忙，请稍后重试");
                    return getPdfVarifyReturnXml(FILE_NO, "服务器繁忙，请稍后重试", "0", beginTime);
                }
                if (System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1) {

                    String v = srvSealUtil.verify(nObjID);
                   /* Map m = varifyDateToMap(v);
                    if (Integer.parseInt(m.get("RetCode")+"")  >= 0) {
                        return getPdfVarifyReturnXml(FILE_NO, "文档验证成功,文档未被篡改", "1", beginTime);
                        //return getPdfVarifyReturnXml(FILE_NO, "文档验证成功，印章：" + m.get("NodeName") + ";证书：" + m.get("CertSubject") + ";序列号：" + m.get("CertSerial") + "证书颁发者：" + m.get("CertIssuer"), "1", beginTime);
                    } else {
                        return getPdfVarifyReturnXml(FILE_NO, "文档验证失败，文档被篡改", "0", beginTime);
                    }*/

                    //List l=varifyDateToMap(v);

                    boolean b = varifyDate(v);
                    if (b == true) {
                        return getPdfVarifyReturnXml(FILE_NO, "文档验证成功,文档未被篡改", "1", beginTime);
                    } else {
                        return getPdfVarifyReturnXml(FILE_NO, "文档验证失败，文档被篡改", "0", beginTime);
                    }

                } else {
                    String sealval = srvSealUtil.getNextSeal(nObjID, "");
                    System.out.println("sealval:" + sealval);
                    String verifyValue = "";
                    if (sealval.equals("") || sealval == null) {
                        return getPdfVarifyReturnXml(FILE_NO, "未发现签名数据，请检查待验证文档是否为加盖了印章的PDF文档!", "0", beginTime);
                    }
                    String sealtype = srvSealUtil.getSealInfo(nObjID, sealval, 0);
                    System.out.println("sealtype" + sealtype);
                    if (sealtype.equals("1")) {
                        return "验证不通过:此盖章pdf中有非印章的元素";
                    }
                    String verifyValue1 = "";
                    while (!sealval.equals("")) {
                       /* byte[] sealP7=srvSealUtil.getSealP7(nObjID,sealval);
                        byte[] data = srvSealUtil.getSealSignSHAData(nObjID,sealval);
                        verifyValue1=SignUtil.verifyP71(Base64.encodeBase64String(sealP7),data);
                        if (verifyValue1.equals("false")) {
                            return getPdfVarifyReturnXml(FILE_NO, "文档验证失败，文档被篡改", "0", beginTime);
                        }*/
                        /*byte[] bytecert=srvSealUtil.getSealAIPCert(nObjID, sealval);
                        System.out.println("bytecert" + bytecert);
                        byte[] byteoridata=srvSealUtil.getSealAIPOriData(nObjID, sealval);
                        System.out.println("byteoridata" + byteoridata);
                        byte[] byteaipsign=srvSealUtil.getSealAIPSign(nObjID, sealval);
                        System.out.println("byteaipsign" + byteaipsign);
                        */
                        String verifySeal = srvSealUtil.verifySeal(nObjID, sealval);
                        boolean b = varifyDate(verifySeal);
                        log.info("linux验证文档的varifyDate-----------：" + verifySeal);
                        log.info("linux验证文档的b------------：" + b);
                        if (b == false) {
                            return getPdfVarifyReturnXml(FILE_NO, "文档验证失败，文档被篡改", "0", beginTime);
                        }
                        return getPdfVarifyReturnXml(FILE_NO, "文档验证成功,文档未被篡改", "1", beginTime);
                    }

                    return getPdfVarifyReturnXml(FILE_NO, "文档验证成功,文档未被篡改", "1", beginTime);

                }
            } catch (Exception e) {
                return getPdfVarifyReturnXml(FILE_NO, e.getMessage(), "1", beginTime);
            } finally {
                documentCreating.saveFile(nObjID, "", syntheticType, 0);
                String filePath = (String) filePaths.get("fileUrl");
                if (filePath != null) {
                    Util.deleteFile(filePath);
                }
            }


        } catch (DocumentException e) {
            return getPdfVarifyReturnXml(null, e.getMessage(), "0", beginTime);
        } catch (Exception e) {
            e.printStackTrace();
            return getPdfVarifyReturnXml(null, e.getMessage(), "0", beginTime);
        }
    }

    /**
     * 得到返回报文（合成接口用）
     *
     * @param map
     * @param folder    合成后文档目录
     * @param beginTime
     * @param checkMsg  验证错误标记
     * @return 响应报文
     */
    private String getReturnXml(Map map, String folder, long beginTime, SyntheticPattern syntheticPattern, String... checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String requestUrl = request.getRequestURL().toString();
        String baseUrl = requestUrl.substring(0, requestUrl.indexOf(Util.getSystemDictionary("server.contextPath")));
        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" + (syntheticPattern == SyntheticPattern.AddSeal ? "<SEAL_DOC_RESPONSE>" : "<MODEL_REQUEST>");
        String msg = "";
        if (checkMsg.length == 0) {
            msg = "<RET_CODE>" + 1 + "</RET_CODE>"
                    + "<RET_MSG>xml验证成功</RET_MSG>";
            Iterator iterator = map.values().iterator();
            msg += "<FILE_LIST>";
            while (iterator.hasNext()) {
                Map m = (Map) iterator.next();
                msg += "<FILE><RET_CODE>" + m.get("RET_CODE") + "</RET_CODE>"
                        + "<FILE_NO>" + m.get("FILE_NO") + "</FILE_NO>"
                        + "<FILE_MSG>" + m.get("FILE_MSG") + "</FILE_MSG>"
                        //  + "<FILE_URL>" + (Integer.parseInt(m.get("RET_CODE") + "") == 0 ? "" : ("http://" + request.getLocalAddr() + ":" + request.getLocalPort() + "" + Util.getSystemDictionary("server.contextPath") + "/file/" + folder + "?name=" + m.get("FILE_NO"))) + "</FILE_URL></FILE>";
                        + "<FILE_URL>" + (Integer.parseInt(m.get("RET_CODE") + "") == 0 ? "" : (baseUrl + Util.getSystemDictionary("server.contextPath") + "/file/" + folder + "?name=" + m.get("FILE_NO"))) + "</FILE_URL></FILE>";
            }
            msg += "</FILE_LIST>";
        } else {
            msg += "<RET_CODE>" + 0 + "</RET_CODE>"
                    + "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "<FILE_LIST></FILE_LIST>";
        }
        retXml += "<SEAL_TIME>" + (new Date().getTime() - beginTime) + "</SEAL_TIME>"
                + msg
                + (syntheticPattern == SyntheticPattern.AddSeal ? "</SEAL_DOC_RESPONSE>" : "</MODEL_REQUEST>");
        return retXml;
    }

    private String getPdfVarifyReturnXml(String fileNo, String retMsg, String regCode, String beginTime) {
        StringBuffer sb = new StringBuffer("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        sb.append("<VERIFY_DOC_RESPONSE>");
        sb.append("<RET_CODE>").append(regCode).append("</RET_CODE>");//1 代表校验通过
        if (fileNo != null) {
            sb.append("<FILE_NO>").append(fileNo).append("</FILE_NO>");
        }
        sb.append("<RET_MSG>").append(retMsg).append("</RET_MSG>");
        sb.append("</VERIFY_DOC_RESPONSE>");
        return sb.toString();
    }

    private List<Map> varifyDateToMap(String varifyDate) {
        if (varifyDate != null && !varifyDate.equals("")) {
            List returnList = new ArrayList();

            String[] s = varifyDate.split("<+");
            for (int j = 0; j < s.length; j++) {
                Map retMap = new HashMap();
                varifyDate = varifyDate.substring(varifyDate.indexOf("<+") + 2, varifyDate.indexOf("/;->"));
                String[] nodes = varifyDate.split("/;");
                for (int i = 0; i < nodes.length; i++) {
                    String[] node = nodes[i].split("=");
                    retMap.put(node[0], node[1]);
                }
                returnList.add(retMap);
            }
            return returnList;
        } else {
            return null;
        }

    }


    private boolean varifyDate(String varifyDate) {
        if (varifyDate != null && !varifyDate.equals("")) {
            String[] s = varifyDate.split("<+");
            for (int j = 1; j < s.length; j++) {
                // Map retMap = new HashMap();
                String s1 = s[j].substring(s[j].indexOf("+") + 1, s[j].indexOf("/;->"));
                String[] nodes = s1.split("/;");
                for (int i = 0; i < nodes.length; i++) {
                    String[] node = nodes[i].split("=");
                    //retMap.put(node[0], node[1]);
                    if (node[0].equals("RetCode")) {
                        if (Integer.parseInt(node[1]) < 0)
                            return false;
                    }
                }

            }
            return true;
        } else {
            return false;
        }

    }

    /**
     * pdf添加水印
     *
     * @param xmlStr    请求报文
     * @param beginTime 开始时间
     * @param request
     * @return
     */
    public String addWatermarkToPdf(String xmlStr, String beginTime, HttpServletRequest request) {
        /*用于封装返回报文信息*/
        String returnXml = "";
        Map retMap = new HashMap();
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        try {
            /*获取报文中关于水印的信息*/
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            Element TREE_NODE = sealDocRequest.element("FILE_LIST").element("TREE_NODE");
            String IS_WATERMARK = TREE_NODE.elementText("IS_WATERMARK");
            String FILE_NO = TREE_NODE.elementText("FILE_NO");//文件名
            String REQUEST_TYPE = TREE_NODE.elementText("REQUEST_TYPE");//读取文件的方式ftp或者http

            /* pdf文件下载到本地*/
            Map filePaths = new HashMap();
            String FileDownRet = null;
            //String ftpEncoding=null;
            if ("1".equals(REQUEST_TYPE)) {//ftp
            } else {//http
                String FILE_PATH = TREE_NODE.elementText("FILE_PATH");//文件下载路径
                FileDownRet = SignatureFileUploadAndDownLoad.httpDownFile(FILE_PATH, beginTime, filePaths, null);
            }
            /*下载失败返回文件名*/
            if (!"ok".equals(FileDownRet)) {
                return getPdfVarifyReturnXml(FILE_NO, FileDownRet, "0", beginTime);
            }

            /*根据下载后的文件地址，生成pdf文档id*/
            int nObjID = documentCreating.openObj((String) filePaths.get("fileUrl"), 0, 0);

            if ("1".equals(IS_WATERMARK)) {
                /*取水印信息*/
                String WATERMARK_MODE = TREE_NODE.elementText("WATERMARK_MODE");//水印模式 short 设置或返回水印模式： 1：居中 (文字)2：平铺 (文字)3：居中带阴影(文字)4：平铺带阴影(文字)7：指定像素值
                String WATERMARK_ALPHA = TREE_NODE.elementText("WATERMARK_ALPHA");//水印透明度值范围：1到63，愈大愈透明。
                String WATERMARK_TYPE = TREE_NODE.elementText("WATERMARK_TYPE");//水印类型1是文字水印2是图片水印
                String WATERMARK_TEXTORPATH = TREE_NODE.elementText("WATERMARK_TEXTORPATH");//文字水印信息或图片base64数据
                String WATERMARK_POSX = TREE_NODE.elementText("WATERMARK_POSX");//水印在文档的x坐标位置
                String WATERMARK_POSY = TREE_NODE.elementText("WATERMARK_POSY");//水印在文档的y坐标位置
                String WATERMARK_TEXTCOLOR = TREE_NODE.elementText("WATERMARK_TEXTCOLOR");//水印文字颜色
                String WATERMARK_ANGLE = TREE_NODE.elementText("WATERMARK_ANGLE");//旋转角度
                String WATERMARK_TXTHORIMGZOOM = TREE_NODE.elementText("WATERMARK_TXTHORIMGZOOM");//缩放比例*/

                /*添加水印信息*/
                init();//初始化控件
                int l = documentCreating.login(nObjID, 2, "HWSEALDEMOXX", "");
                log.info(beginTime + ":login:" + l);
                if (l != 0) {
                    throw new Exception("未授权的服务器");
                }
                srvSealUtil.setValue(nObjID, "SET_WATERMARK_MODE", WATERMARK_MODE);
                if ("1".equals(WATERMARK_TYPE)) {//文字水印
                    srvSealUtil.setValue(nObjID, "SET_WATERMARK_TEXTORPATH", "STRDATA:" + WATERMARK_TEXTORPATH);

                } else if ("2".equals(WATERMARK_TYPE)) {//图片水印
                    srvSealUtil.setValue(nObjID, "SET_WATERMARK_TEXTORPATH", WATERMARK_TEXTORPATH);
                    srvSealUtil.setValue(nObjID, "SET_WATERMARK_TEXTCOLOR", WATERMARK_TEXTCOLOR);
                }
                srvSealUtil.setValue(nObjID, "SET_WATERMARK_ALPHA", WATERMARK_ALPHA);
                srvSealUtil.setValue(nObjID, "SET_WATERMARK_POSX", WATERMARK_POSX);
                srvSealUtil.setValue(nObjID, "SET_WATERMARK_POSY", WATERMARK_POSY);
                srvSealUtil.setValue(nObjID, "SET_WATERMARK_ANGLE", WATERMARK_ANGLE);
                srvSealUtil.setValue(nObjID, "SET_WATERMARK_TXTHORIMGZOOM", WATERMARK_TXTHORIMGZOOM);

                String savePath = filePath + "/" + FILE_NO.substring(0, FILE_NO.lastIndexOf(".")) + "." + syntheticType;
                int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 0);

                log.info(beginTime + ":saveFile:" + saveFileRet);
                if (saveFileRet == 0) {
                    retMap.put("RET_CODE", "1失败");
                    retMap.put("FILE_MSG", "水印添加失败");
                    log.info(beginTime + ":saveFile文档保存失败，请检查服务器，保存路径：" + savePath);
                    throw new Exception("saveFile文档保存失败");
                } else {
                    log.info(beginTime + ":saveFile文档保存成功" + new Date() + "保存路径：" + savePath);
                    retMap.put("RET_CODE", "0成功");
                    retMap.put("FILE_MSG", "文档添加水印成功");
                    retMap.put("FILE_NO", FILE_NO);
                }
                returnXml = getReturnXml(retMap, filePath.substring(filePath.lastIndexOf("/") + 1), beginTime);
            } else {
                log.info("报文显示无需添加水印");
                throw new Exception("xml显示无需添加水印");
            }
        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getReturnXml(null, "", beginTime, "xml解析失败");
        } catch (Exception e) {
            e.printStackTrace();
            return getReturnXml(null, "", beginTime, e.getMessage());
        } finally {
            log.info("saveFile文档关闭");
        }
        return returnXml;
    }


    /**
     * 得到返回报文（pdf添加水印用）
     *
     * @param map
     * @param folder    合成后文档目录
     * @param beginTime
     * @param checkMsg  验证错误标记
     * @return 响应报文
     */
    private String getReturnXml(Map map, String folder, String beginTime, String... checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String requestUrl = request.getRequestURL().toString();
        String baseUrl = requestUrl.substring(0, requestUrl.indexOf(Util.getSystemDictionary("server.contextPath")));
        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><WATERMARK_RESPONSE>";
        if (checkMsg.length == 0) {
            retXml += "<META_DATA>";//http://127.0.0.1:
            retXml += "<RET_CODE>" + map.get("RET_CODE") + "</RET_CODE>"
                    + "<FILE_MSG>" + map.get("FILE_MSG") + "</FILE_MSG>"
                    + "<FILE_NO>" + map.get("FILE_NO") + "</FILE_NO>"
                    //  + "<FILE_URL>" + ("http://" + request.getLocalAddr() + ":" + request.getLocalPort() + "" + Util.getSystemDictionary("server.contextPath") + "/file/" + folder + "?name=" + map.get("FILE_NO"))+ "</FILE_URL>";
                    + "<FILE_URL>" + (baseUrl + Util.getSystemDictionary("server.contextPath") + "/file/" + folder + "?name=" + map.get("FILE_NO")) + "</FILE_URL>";
            retXml += "</META_DATA>";
        } else {
            retXml += "<RET_CODE>1失败</RET_CODE>"
                    + "<FILE_MSG>" + checkMsg[0] + "</FILE_MSG>"
                    + "<FILE_LIST></FILE_LIST>";
        }
        retXml += "</WATERMARK_RESPONSE>";
        return retXml;
    }

    /**
     * 服务端签章 文档信息保存
     *
     * @param fileNo      文件编号
     * @param creator     文件创建者 存sysid
     * @param creatorName 创建者名称
     * @param filePath    盖章或者合成后文件路径
     * @param sourceType  2 服务端签章, 3 服务端合成
     * @return
     */
    private boolean saveServerDocument(String fileNo, String creator, String creatorName, String filePath, byte sourceType) {
        com.dianju.modules.document.models.Document document = new com.dianju.modules.document.models.Document();
        document.setSn(fileNo);//文档号
        document.setCreator(creator);//创建人
        document.setCreatorName(creatorName);//创建人名称
        document.setFilePath(filePath);//文档保存路径
        document.setSourceType(sourceType);//文档类型
        document.setName(fileNo.substring(0, fileNo.lastIndexOf(".")));
        document.setDoStatus((byte) 2);//办理状态
        document.setDeptNo("0000000000000000000001");//设置部门号
        try {
            documentDao.save(document);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    /**
     * 文档合成接口
     *
     * @param xmlStr           请求报文
     * @param syntheticPattern 类型
     * @param fileId
     * @param request
     * @return 响应报文
     */
    public String sealAutoPdfZF(String retXmlType, String xmlStr, SyntheticPattern syntheticPattern, String fileId, HttpServletRequest request) {
        //创建所有服务端签章需要的文件夹(如果没有则创建)
        initDir();
        long beginTime = System.currentTimeMillis();
        LogServerSeal logServerSeal = new LogServerSeal();
        logServerSeal.setId(fileId);
/*
        logServerSeal.setRequestXml(xmlStr);
*/
        String returnXml = "";

        try {
            varifySystemType();
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            SealAutoPdfCheckZF check = new SealAutoPdfCheckZF(fileId + "", syntheticPattern);//与ofd平台不一样
            logServerSeal.setCreatedAt(Util.getTimeStampOfNow());
            //XML格式与内容判断
            if (!check.sealAutoPdf(sealDocRequest, request)) {
                log.info(fileId + ":xml格式判断:" + check.getError());
                logServerSeal.setResult(0);
                returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, check.getError());
            } else {
                log.info(fileId + ":xml格式判断:成功");
                logServerSeal.setResult(1);
                Element META_DATA = sealDocRequest.element("META_DATA");
                String IS_MERGER = META_DATA.elementText("IS_MERGER");
                META_DATA.addElement("IP", "").addText(request.getRemoteAddr());
                log.info(META_DATA.elementText("IP"));
                Map sealAutoPdfForMerageRet = new HashMap();
                if ("true".equals(IS_MERGER)) {
                    sealAutoPdfForMerageRet = sealAutoPdfForMerageZF(sealDocRequest, syntheticPattern, fileId);
                    returnXml = getReturnXml(retXmlType, sealAutoPdfForMerageRet, "sealFilePath", beginTime, syntheticPattern);
                } else {
                    sealAutoPdfForMerageRet = sealAutoPdfForNotMergeZF(sealDocRequest, syntheticPattern, fileId);
                    returnXml = getReturnXml(retXmlType, sealAutoPdfForMerageRet, "sealFilePath", beginTime, syntheticPattern);
                }
                //记录日志
                Map<String, String> map = check.params;
                LogFileServerSeal logFileServerSeal = new LogFileServerSeal();
                logFileServerSeal.setSystemId(map.get("SYS_ID"));
                logFileServerSeal.setIpAddress(request.getRemoteAddr());
                logFileServerSeal.setLogServerSealId(logServerSeal.getId());
                logFileServerSeal.setDescription(SyntheticPattern.AddSeal == syntheticPattern ? "签章" : "合成");

                Iterator iterator = sealAutoPdfForMerageRet.values().iterator();
                while (iterator.hasNext()) {
                    Map m = (Map) iterator.next();
                    if ("0".equals(m.get("RET_CODE"))) {
                        logServerSeal.setResult(0);
                    }
                    logFileServerSeal.setResult(Integer.parseInt(m.get("RET_CODE") + ""));
                    logFileServerSeal.setDocumentName((String) m.get("REQUEST_FILE_NO"));

                    if (map.containsKey("RULE_NO")) {
                        logFileServerSeal.setRuleNo(map.get("RULE_NO"));
                    }
                    logFileServerSeal.setRuleInfo(map.get("RULE_INFO"));
                    logFileServerSeal.setSealId(map.get("SEAL_ID"));

                    logFileServerSealDao.save(logFileServerSeal);
                }
            }
        } catch (DocumentException e) {
            log.error(fileId + "");
            e.printStackTrace();
            returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, "xml解析失败");
        } catch (Exception e) {
            log.error(fileId + "");
            e.printStackTrace();
            returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, "模版合成与盖章失败" + e.getMessage());
        } finally {
/*
            logServerSeal.setResponseXml(returnXml);
*/
            logServerSealDao.save(logServerSeal);
            return returnXml;
        }
    }

    private void varifySystemType() throws DJException {
        if (((LicenseConfig.systemType & LicenseConfig.SystemType_Server) == LicenseConfig.SystemType_Server) ||
                ((LicenseConfig.systemType1 & LicenseConfig.SystemType1_GJZWYY) == LicenseConfig.SystemType1_GJZWYY)
        ) {
        } else {
            String str = "拒绝服务：未授权的服务器类型";
            log.info(str);
            throw new DJException(str);
        }
    }

    /**
     * 得到返回报文（合成接口用）
     *
     * @param map
     * @param folder   合成后文档目录
     * @param checkMsg 验证错误标记
     * @return 响应报文
     */
    public String getReturnXml(String type, Map map, String folder, long beginTime, SyntheticPattern syntheticPattern, String... checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String requestUrl = request.getRequestURL().toString();
        String baseUrl = requestUrl.substring(0, requestUrl.indexOf(Util.getSystemDictionary("server.contextPath")));

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" + (syntheticPattern == SyntheticPattern.AddSeal ? "<SEAL_DOC_RESPONSE>" : "<MODEL_REQUEST>");
        String msg = "";
        if (checkMsg.length == 0) {
            msg = "<RET_CODE>" + 1 + "</RET_CODE>"
                    + "<RET_MSG>xml验证成功</RET_MSG>";
            Iterator iterator = map.values().iterator();
            msg += "<FILE_LIST>";
            while (iterator.hasNext()) {
                Map m = (Map) iterator.next();

                String fileData = "";
                if ((Integer.parseInt(m.get("RET_CODE") + "") == 1)) {
                    String filePath = Util.getSystemDictionary("upload_path") + "/" + folder + "/" + m.get("SUFFIX_FOLDER") + "/" + m.get("FILE_NO");
                    if (m.containsKey("RET_FILE_TYPE") && "BASE64".equals(m.get("RET_FILE_TYPE")) || m.containsKey("FILE_RETURN_TYPE") && "BASE64".equals(m.get("FILE_RETURN_TYPE"))) {
                        log.info("filePath-----------------------------------" + filePath);
                        fileData = "<FILE_DATA>" + Util.getFileBase642(filePath) + "</FILE_DATA>";//++
                    } else if (m.containsKey("FILE_RETURN_TYPE") && "DISC".equals(m.get("FILE_RETURN_TYPE"))) {
                        if (!m.containsKey("FILE_RETURN_PATH")) {
                            fileData = "<FILE_URL>未指定返回文件磁盘保存路径</FILE_URL>";
                        } else {
                            String discPath = m.get("FILE_RETURN_PATH").toString() + m.get("REQUEST_FILE_NO");
                            Util.copyFile(filePath, discPath);
                            fileData = "<FILE_URL>" + discPath + "</FILE_URL>";
                            m.put("FILE_NO", m.get("REQUEST_FILE_NO"));
                        }
                    } else {
                        String path = "";
                        if (syntheticPattern == SyntheticPattern.AddSeal || syntheticPattern == SyntheticPattern.AddEmptySeal) {
                            path = "/file/sealFilePath/";
                        } else {
                            path = "/file/filePath/";
                        }
/*
                        fileData= "<FILE_URL>" + (Integer.parseInt(m.get("RET_CODE") + "") == 0 ? "" : ("http://" + request.getLocalAddr() + ":" + request.getLocalPort() + "" + Util.getSystemDictionary("server.contextPath") + "/file/sealFilePath/"+m.get("SUFFIX_FOLDER")+"?name=" + m.get("FILE_NO"))).toString() + "</FILE_URL>";
*/
                        fileData = "<FILE_URL>" + (Integer.parseInt(m.get("RET_CODE") + "") == 0 ? "" : (baseUrl + Util.getSystemDictionary("server.contextPath") + path + m.get("SUFFIX_FOLDER") + "?name=" + m.get("FILE_NO"))) + "</FILE_URL>";


                    }
                }
                String retCode = (String) m.get("RET_CODE");
                if (type.equals("jit") && retCode.equals("0")) {
                    retCode = "-1002";
                }
                msg += "<FILE><RET_CODE>" + retCode + "</RET_CODE>"
                        + "<REQUEST_FILE_NO>" + m.get("REQUEST_FILE_NO") + "</REQUEST_FILE_NO>"
                        + "<FILE_NO>" + m.get("FILE_NO") + "</FILE_NO>"
                        + "<FILE_MSG>" + m.get("FILE_MSG") + "</FILE_MSG>"
                        + fileData + "</FILE>";
            }
            msg += "</FILE_LIST>";
        } else {
            if (type.equals("jit")) {
                msg += "<RET_CODE>" + "-1002" + "</RET_CODE>";
            } else {
                msg += "<RET_CODE>" + 0 + "</RET_CODE>";
            }

            msg += "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "<FILE_LIST></FILE_LIST>";
        }
        retXml += "<SEAL_TIME>" + (new Date().getTime() - beginTime) + "</SEAL_TIME>"
                + msg
                + (syntheticPattern == SyntheticPattern.AddSeal ? "</SEAL_DOC_RESPONSE>" : "</MODEL_REQUEST>");
        return retXml;
    }

    /**
     * 文档合成（合并）
     *
     * @param sealDocRequest
     * @param syntheticPattern
     * @param fileId
     * @return
     */

    private Map sealAutoPdfForMerageZF(Element sealDocRequest, SyntheticPattern syntheticPattern, String fileId) throws Exception {
        init();
        Map retMap = new HashMap();
        Element META_DATA = sealDocRequest.element("META_DATA");
        String syntheticType = META_DATA.elementText("DOC_TYPE");
        if (syntheticType == null || syntheticType.equals("")) {
            syntheticType = Util.getSystemDictionary("synthetic_type");
        } else {
            syntheticType = syntheticType.trim().toLowerCase();
        }
        log.info(fileId + "->" + META_DATA.elementText("FILE_NO"));
        retMap.put("REQUEST_FILE_NO", META_DATA.elementText("FILE_NO"));
        Element RET_FILE_TYPE = META_DATA.element("RET_FILE_TYPE");
        if (RET_FILE_TYPE != null) {
            retMap.put("RET_FILE_TYPE", RET_FILE_TYPE.getTextTrim().toUpperCase());
        }
        String suffixFolder = Util.getYYYYMMDDHH();
        retMap.put("SUFFIX_FOLDER", suffixFolder);

        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map downFileMap = new HashMap<Integer, String>();
        String fileDownRet = SignatureFileUploadAndDownLoad.downFile(TREE_NODES, fileId, downFileMap, Pattern.End);
        if (!"ok".equals(fileDownRet)) {
            throw new Exception(fileDownRet);
        }
        String savePath = Util.getSuffixFolder(filePath + "/", suffixFolder) + fileId + "." + syntheticType;
        int nObjID = documentCreating.openObj("", 0, 0);
        log.info(fileId + ":nObjID:" + nObjID);
        try {
            if (nObjID <= 0) {
                log.info(fileId + ":服务器繁忙，请稍后重试1");
                throw new Exception("服务器繁忙，请稍后重试1");
            }
            int l = documentCreating.login(nObjID, 2, "dj", "");
            log.info(fileId + ":login:" + l);
            if (l != 0) {
                throw new Exception("未授权的服务器");
            }
            String makeMergerFileRet = documentCreating.makeMergerFile(nObjID, FILE_LIST, downFileMap, fileId);
            if (!"ok".equals(makeMergerFileRet)) {
                retMap.put("FILE_MSG", makeMergerFileRet);
                throw new Exception(makeMergerFileRet);
            }
            String insertCodeBarret = documentCreating.insertCodeBar(nObjID, META_DATA, fileId);
            if (!"ok".equals(insertCodeBarret)) {
                throw new Exception(insertCodeBarret);
            }
            int saveFileRet = documentCreating.saveFile(nObjID, savePath, syntheticType, 1);
            log.info(fileId + ":saveFilePath：" + savePath);
            log.info(fileId + ":saveFile:" + saveFileRet);
            if (saveFileRet == 0) {
                throw new Exception("saveFile文档保存失败");
            }
        } catch (Exception e) {
            throw e;
        } finally {
            documentCreating.saveFile(nObjID, "", syntheticType, 0);
        }
        try {
            if (syntheticPattern == SyntheticPattern.AddSeal || syntheticPattern == SyntheticPattern.AddEmptySeal) {
                String sfp = Util.getSuffixFolder(sealFilePath, suffixFolder) + fileId + "." + syntheticType;
                String addSealret = "";
                if (syntheticPattern == SyntheticPattern.AddSeal) {
                    addSealret = documentCreating.addSeal(savePath, sfp, META_DATA, fileId, syntheticType, retMap);
                } else {
                    //适配新疆签空白章需求
                    addSealret = documentCreating.addSeal2(savePath, sfp, META_DATA, fileId, syntheticType);
                }
                if (!"ok".equals(addSealret)) {
                    Util.deleteFile(sfp);
                    throw new Exception(addSealret);
                }
                savePath = sfp;
/*
                retMap.put("FILE_NO", SEAL_FILE_PATH+"_"+suffixFolder+"_"+fileId+"."+syntheticType);
*/
                retMap.put("FILE_NO", fileId + "." + syntheticType);
            } else {
/*
                retMap.put("FILE_NO", FILE_PATH+"_"+suffixFolder+"_"+fileId+"."+syntheticType);
*/
                retMap.put("FILE_NO", fileId + "." + syntheticType);
            }
            if (META_DATA.elementText("FTP_SAVEPATH") != null && !"".equals(META_DATA.elementText("FTP_SAVEPATH"))) {
                String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(META_DATA, savePath, fileId);
                if ("ok".equals(ftpUpFileRet)) {
                    retMap.put("RET_CODE", "1");
                    retMap.put("FILE_MSG", "文档上传成功");
                } else {
                    retMap.put("RET_CODE", "0");
                    retMap.put("FILE_MSG", ftpUpFileRet);
                }

            } else {
                retMap.put("RET_CODE", "1");
                retMap.put("FILE_MSG", "文档合成成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
            retMap.put("RET_CODE", "0");
            retMap.put("FILE_MSG", e.getMessage());
        }
        Map m = new HashMap();
        m.put(0, retMap);
        return m;
    }

    /**
     * 文档合成不合并
     *
     * @param sealDocRequest
     * @param syntheticPattern
     * @param fileId
     * @return
     */
    private Map sealAutoPdfForNotMergeZF(Element sealDocRequest, SyntheticPattern syntheticPattern, String fileId) throws Exception {
        init();
        String suffixFolder = Util.getYYYYMMDDHH();
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map msgMap = new HashMap<Integer, Map<String, String>>();
        SignatureFileUploadAndDownLoad.downFile(TREE_NODES, fileId, msgMap, Pattern.Next);
        documentCreating.templateSynthesisZF(TREE_NODES, fileId, msgMap);
        for (int i = 0; i < TREE_NODES.size(); i++) {
            String syntheticType = TREE_NODES.get(i).elementText("DOC_TYPE");
            if (syntheticType == null || "".equals(syntheticType)) {
                syntheticType = Util.getSystemDictionary("synthetic_type");
            } else {
                syntheticType = syntheticType.trim().toLowerCase();
            }
            Map thisMsg = (Map) msgMap.get(i);

            thisMsg.put("IP", sealDocRequest.element("META_DATA").elementText("IP"));
            thisMsg.put("SUFFIX_FOLDER", suffixFolder);
            Element TREE_NODE = TREE_NODES.get(i);
            log.info(thisMsg.get("FILE_NO") + "->" + TREE_NODE.elementText("FILE_NO"));
            thisMsg.put("REQUEST_FILE_NO", TREE_NODE.elementText("FILE_NO"));
            Element RET_FILE_TYPE = TREE_NODE.element("RET_FILE_TYPE");
            if (RET_FILE_TYPE != null) {
                thisMsg.put("RET_FILE_TYPE", RET_FILE_TYPE.getTextTrim().toUpperCase());
            }
            int ret = 0;
            if ("1".equals(thisMsg.get("RET_CODE") + "")) {
                String filePath = (String) thisMsg.get("FILE_MSG");
                String fileNo = (String) thisMsg.get("FILE_NO");
                if (syntheticPattern == SyntheticPattern.AddSeal || syntheticPattern == SyntheticPattern.AddEmptySeal) {
                    String sfp = Util.getSuffixFolder(sealFilePath, suffixFolder) + fileNo + "." + syntheticType;
                    String addSealret = "ok";
                    if (syntheticPattern == SyntheticPattern.AddEmptySeal) {
                        // 对接接口docSign
                        addSealret = documentCreating.addSeal2(filePath, sfp, TREE_NODE, fileNo, syntheticType);
                    } else {
                        addSealret = documentCreating.addSeal(filePath, sfp, TREE_NODE, fileNo, syntheticType, thisMsg);
                    }
                    if (!"ok".equals(addSealret)) {
                        /*失败但生成了盖章文件，需要删除,一般在上传盖章日志时生成*/
                        Util.deleteFile(sfp);
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", addSealret);
                    } else {
                        thisMsg.put("FILE_MSG", sfp);
                        // thisMsg.put("FILE_NO",SEAL_FILE_PATH+"_"+ thisMsg.get("SUFFIX_FOLDER")+"_"+thisMsg.get("FILE_NO")+"."+syntheticType);
                        thisMsg.put("FILE_NO", thisMsg.get("FILE_NO") + "." + syntheticType);
                        ret = 1;
                    }
                } else {

                    //thisMsg.put("FILE_NO",FILE_PATH+"_"+ thisMsg.get("SUFFIX_FOLDER")+"_"+thisMsg.get("FILE_NO")+"."+syntheticType);
                    thisMsg.put("FILE_NO", thisMsg.get("FILE_NO") + "." + syntheticType);
                    ret = 1;
                }
                if (ret == 1) {
                    if (TREE_NODE.elementText("FTP_SAVEPATH") != null && !"".equals(TREE_NODE.elementText("FTP_SAVEPATH"))) {
                        String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(TREE_NODE, thisMsg.get("FILE_MSG") + "", thisMsg.get("REQUEST_FILE_NO") + "");
                        if ("ok".equals(ftpUpFileRet)) {
                            thisMsg.put("RET_CODE", "1");
                            thisMsg.put("FILE_MSG", "文档上传成功");
                        } else {
                            thisMsg.put("RET_CODE", "0");
                            thisMsg.put("FILE_MSG", ftpUpFileRet);
                        }

                    } else {
                        thisMsg.put("RET_CODE", "1");
                        thisMsg.put("FILE_MSG", "文档合成成功");
                    }
                }
            }
            thisMsg.put("FILE_RETURN_TYPE", TREE_NODE.elementText("FILE_RETURN_TYPE"));
            if (TREE_NODE.elementText("FILE_RETURN_PATH") != null) {
                thisMsg.put("FILE_RETURN_PATH", TREE_NODE.elementText("FILE_RETURN_PATH"));
            }

        }
        return msgMap;
    }

    /**
     * 文档验证接口,来自ofd版本
     *
     * @param xmlStr  请求报文
     * @param fileId  开始时间
     * @param request
     * @return 响应报文
     */
    public String pdfVarify(String xmlStr, String fileId, String retXmlType, HttpServletRequest request, OperationType operationType) {
        try {
            init();
            Document doc = DocumentHelper.parseText(xmlStr);
            Element verifyDocRequest = doc.getRootElement();
            PdfVarifyCheck check = new PdfVarifyCheck(fileId);
            boolean result = check.pdfVarifyZF(verifyDocRequest, request); //可以不用传FILE_NO
            String FILE_PATH = null;
            String FILE_NO = null;
            String FILE_TYPE = null;
            String FTP_ADDRESS = null;
            String FTP_PORT = null;
            String FTP_USER = null;
            String FTP_PWD = null;
            String FILE_SUFFIX = null;
            if (!result) {
                log.info("xml格式判断:" + check.getError());
                try {
                    return getPdfVarifyReturnXml(retXmlType, null, check.getError(), "0", fileId, verifyDocRequest.element("META_DATA").elementText("FILE_SUFFIX"), null, operationType);
                } catch (Exception e) {
                    return getPdfVarifyReturnXml(retXmlType, null, check.getError(), "0", fileId, null, null, operationType);
                }
            } else {
                Element META_DATA = verifyDocRequest.element("META_DATA");
                FILE_PATH = META_DATA.elementText("FILE_PATH");
/*
                FILE_NO = META_DATA.elementText("FILE_NO");
*/
                FILE_TYPE = META_DATA.elementText("FILE_TYPE");
                FILE_SUFFIX = META_DATA.elementText("FILE_SUFFIX");

                if (!"pdf".equalsIgnoreCase(FILE_SUFFIX) && !"ofd".equalsIgnoreCase(FILE_SUFFIX)) {
                    return getPdfVarifyReturnXml(retXmlType, FILE_NO, "错误的FILE_SUFFIX", "0", fileId, FILE_SUFFIX, null, operationType);
                }
                if (FILE_TYPE == null) {
                    FILE_TYPE = "0";
                }
                if (FILE_TYPE.equals("1")) {
                    FTP_ADDRESS = META_DATA.elementText("FTP_ADDRESS");
                    FTP_PORT = META_DATA.elementText("FTP_PORT");
                    FTP_USER = META_DATA.elementText("FTP_USER");
                    FTP_PWD = META_DATA.elementText("FTP_PWD");
                }
                /*判断是否传FILE_NO*/
                if (META_DATA.element("FILE_NO") != null) {
                    FILE_NO = META_DATA.elementText("FILE_NO");
                    int m = FILE_NO.lastIndexOf(".");
                    if (m < 0) {
                        return getPdfVarifyReturnXml(retXmlType, FILE_NO, "错误的文件名FILE_NO", "0", fileId, FILE_SUFFIX, null, operationType);
                    }
                    if (!FILE_NO.substring(m + 1).equalsIgnoreCase(FILE_SUFFIX)) {
                        return getPdfVarifyReturnXml(retXmlType, FILE_NO, "后缀和文件后缀不一致", "0", fileId, FILE_SUFFIX, null, operationType);
                    }

                }
                log.info("xml格式判断:成功");
            }


            Map filePaths = new HashMap();
            String FileDownRet = null;
            String ftpEncoding = null;
            if (FILE_TYPE.equals("1")) {
                FileDownRet = SignatureFileUploadAndDownLoad.ftpDownFile1(ftpEncoding, FTP_ADDRESS, FTP_PORT, FTP_USER, FTP_PWD, FILE_PATH, fileId, filePaths);
            } else if (FILE_TYPE.equals("3")) {
                /*3.base64方式传入*/
                String fileSuffix = FILE_SUFFIX;

                String fname = fileId + "-" + new Date().getTime() + fileSuffix;
                String saveFilePath = Util.getSystemDictionary("upload_path") + "/downPathHttp/" + fname;

                Util.base64ToFile(FILE_PATH, saveFilePath);
                filePaths.put("fileUrl", saveFilePath);
                filePaths.put("fileName", fname);
                filePaths.put("fileSuffix", fileSuffix);
                FileDownRet = "ok";
            } else {
                FileDownRet = SignatureFileUploadAndDownLoad.httpDownFile(FILE_PATH, fileId, filePaths, null);
            }
            if (!"ok".equals(FileDownRet)) {
                return getPdfVarifyReturnXml(retXmlType, FILE_NO, FileDownRet, "0", fileId, FILE_SUFFIX, null, operationType);
            }
            int nObjID = documentCreating.openObj((String) filePaths.get("fileUrl"), 0, 0);
            log.info(fileId + ":nObjID:" + nObjID);
            try {
                if (nObjID <= 0) {
                    log.info(fileId + ":服务器繁忙，请稍后重试");
                    return getPdfVarifyReturnXml(retXmlType, FILE_NO, "服务器繁忙，请稍后重试", "0", fileId, FILE_SUFFIX, null, operationType);
                }

                EncryptionDevice encryptionDevice = null;
                try {
                    encryptionDevice = (EncryptionDevice) Util.getBean(Util.getSystemDictionary("encryptionDeviceImpl"));
                } catch (Exception e) {
                    log.info(fileId + "未找到签名实现:");
                    return getPdfVarifyReturnXml(retXmlType, FILE_NO, "未找到签名实现:", "0", fileId, FILE_SUFFIX, null, operationType);
                }
                List<Map<String, String>> list = new ArrayList<>();
                String msg = "此章验证成功";
                if (System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") != -1) {
                    int sealCount = srvSealUtil.getNodeNum(nObjID, 251);
                    log.info(fileId + ":sealCount:" + sealCount);
                    if (sealCount == 0) {
                        return getPdfVarifyReturnXml(retXmlType, FILE_NO, "文档没有找到印章", "0", fileId, FILE_SUFFIX, null, operationType);
                    }
                    for (int i = 0; i < sealCount; i++) {
                        //印章在节点里的名称
                        String nodeName = srvSealUtil.getNoteByIndex(nObjID, i, 251);
                        // 获取印章数据
                        Map m = getSealInformation(nObjID, nodeName);
                        m.put("msg", msg);
                        // 存入证书数据
                        list.add(m);

                        /*
                         * 只获取印章信息返回
                         */
                        if (operationType == OperationType.GetInfor) {
                            continue;
                        }
                        // 验证印章有效性
                        JSONObject object = verifySealStatus(m.get("sealName").toString(), m.get("sealCode").toString());
                        if (object == null) {
                            msg = "链接印章发布系统失败";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        } else if ("1".equals(object.get("sealStatus"))) {
                            msg = "文档验签失败,sealCode为" + m.get("sealCode") + "的印章无效";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        }

                        boolean verifyRet = encryptionDevice.verifyP1(Base64.decodeBase64(m.get("ofdoridata").toString()), Base64.decodeBase64(m.get("certData").toString()), Base64.decodeBase64(m.get("ofdsign").toString()));
                        if (!verifyRet) {
                            msg = "文档验证失败,sealCode为" + m.get("sealCode") + "的章验证未通过";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        }
                    }
                    return getPdfVarifyReturnXml(retXmlType, FILE_NO, "文档验证成功,文档未被篡改", "1", fileId, FILE_SUFFIX, list, operationType);
                } else {
                    log.info("验签开始");
                    String sealval = "";
                    int sealIndex = 0;
                    do {
                        sealval = srvSealUtil.getNextSeal(nObjID, sealval);
                        log.info("sealval:" + sealval);

                        if (sealval.equals("") || sealval == null) {
                            msg = "未发现签名数据，请检查待验证文档是否为加盖了印章的文档!";
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, null, operationType);
                        }
                        // 获取印章数据
                        Map m = getSealInformation(nObjID, sealval);
                        m.put("msg", msg);
                        list.add(m);
                        /*
                         * 只获取印章信息返回
                         */
                        if (operationType == OperationType.GetInfor) {
                            continue;
                        }

                        // 验证印章有效性
                        JSONObject object = verifySealStatus(m.get("sealName").toString(), m.get("sealCode").toString());
                        if (object == null) {
                            msg = "链接印章发布系统失败";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        } else if ("1".equals(object.get("sealStatus"))) {
                            msg = "文档验签失败,sealCode为" + m.get("sealCode") + "的印章无效";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        }
                        String sealtype = srvSealUtil.getSealInfo(nObjID, sealval, 0);
                        log.info("sealtype" + sealtype);
                        if (sealtype.equals("1")) {
                            msg = "验证不通过:此盖章文件中有非印章的元素";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        }
                        String verifySeal = srvSealUtil.verifySeal(nObjID, sealval);
                        log.info("verifySeal" + verifySeal);
                        boolean b = varifyDate(verifySeal);
                        log.info("linux验证文档的varifyDate-----------：" + verifySeal);
                        log.info("linux验证文档的b------------：" + b);
                        if (b == false) {
                            msg = "文档验证失败,文档被篡改,sealCode为" + m.get("sealCode") + "的章验证未通过";
                            m.put("msg", msg);
                            return getPdfVarifyReturnXml(retXmlType, FILE_NO, msg, "0", fileId, FILE_SUFFIX, list, operationType);
                        }
                        sealIndex++;
                    } while ("".equals(sealval));
                    return getPdfVarifyReturnXml(retXmlType, FILE_NO, "文档验证成功,文档未被篡改", "1", fileId, FILE_SUFFIX, list, operationType);
                }
            } catch (Exception e) {
                return getPdfVarifyReturnXml(retXmlType, FILE_NO, "签名验证失败," + e.getMessage(), "0", fileId, FILE_SUFFIX, null, operationType);
            } finally {
                log.info(fileId + ":验证完成");
                documentCreating.saveFile(nObjID, "", FILE_SUFFIX, 0);
                Util.deleteFile((String) filePaths.get("fileUrl"));
            }
        } catch (DocumentException e) {
            return getPdfVarifyReturnXml(retXmlType, null, e.getMessage(), "0", fileId, null, null, operationType);
        } catch (Exception e) {
            e.printStackTrace();
            return getPdfVarifyReturnXml(retXmlType, null, e.getMessage(), "0", fileId, null, null, operationType);
        }
    }

    /**
     * 来自ofd版本
     *
     * @param type
     * @param fileNo
     * @param retMsg
     * @param regCode
     * @param fileId
     * @return
     */
    public String getPdfVarifyReturnXml(String type, String fileNo, String retMsg, String regCode, String fileId, String fileSuffix, List<Map<String, String>> list, OperationType operationType) {
        StringBuffer sb = new StringBuffer("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        sb.append("<VERIFY_DOC_RESPONSE>");
        if (regCode.equals("0") && type.equals("jit")) {
            regCode = "-1007";
        }
        sb.append("<RET_CODE>").append(regCode).append("</RET_CODE>");//1 代表校验通过
        if (fileNo != null) {
            sb.append("<FILE_NO>").append(fileNo).append("</FILE_NO>");
        } else {
            sb.append("<FILE_NO></FILE_NO>");
        }
        sb.append("<FILE_SUFFIX>").append(fileSuffix).append("</FILE_SUFFIX>");
        if (operationType == OperationType.PdfVarify) {
            sb.append("<RET_MSG>").append(retMsg).append("</RET_MSG>");
        }
        if (list != null && list.size() > 0) {
            sb.append("<DETAIL>");
            for (Map m : list) {
                sb.append("<CERT_SEAL>");
                sb.append("<SEAL_CODE>").append(m.get("sealCode")).append("</SEAL_CODE>");
                if (operationType == OperationType.GetInfor) {
                    sb.append("<CERT_DATA>").append(m.get("certData")).append("</CERT_DATA>");
                    sb.append("<DOC_SIGN>").append(m.get("ofdsign")).append("</DOC_SIGN>");
                    sb.append("<SEAL_DATA>").append(m.get("sealData")).append("</SEAL_DATA>");
                } else {
                    if (m != null && m.containsKey("sn")) {
                        sb.append("<CERT_SN>").append(m.get("sn")).append("</CERT_SN>");
                    }
                    if (m != null && m.containsKey("dn")) {
                        sb.append("<CERT_DN>").append(m.get("dn")).append("</CERT_DN>");
                    }
                    sb.append("<MSG>").append(m.get("msg")).append("</MSG>");
                }
                sb.append("</CERT_SEAL>");
            }
            sb.append("</DETAIL>");
        }
        sb.append("</VERIFY_DOC_RESPONSE>");
        return sb.toString();
    }

    /**
     * 文件类型转换
     *
     * @param xmlStr    请求报文
     * @param beginTime 开始时间
     * @param request
     * @return
     */
    public String fileConvert(String xmlStr, String beginTime, HttpServletRequest request) {
        /* 用于封装返回报文信息 */
        String returnXml = "";
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        try {
            /* 获取报文信息 */
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            Check check = new Check(beginTime);
            //XML格式与内容判断
            if (!check.checkAdh(sealDocRequest, request)) {
                log.info(":xml格式判断:" + check.getError());
                returnXml = getOfdReturnXml(null, check.getError());
            } else {
                Map msgMap = new HashMap();
                List<Element> TREE_NODES = sealDocRequest.element("FILE_LIST").elements("TREE_NODE");
                SignatureFileUploadAndDownLoad.downFile(TREE_NODES, beginTime, msgMap, Pattern.Next);
                documentCreating.templateSynthesisZF(TREE_NODES, beginTime, msgMap);
                returnXml = getOfdReturnXml(msgMap, null);
            }
        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            returnXml = getOfdReturnXml(null, "xml解析失败");
        } catch (Exception e) {
            e.printStackTrace();
            returnXml = getOfdReturnXml(null, e.getMessage());
        } finally {
        }
        return returnXml;
    }


    /**
     * pdf转ofd用
     * 改成pdf,ofd互转用
     *
     * @param map
     * @return 响应报文
     */
    private String getOfdReturnXml(Map map, String checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<FILE_CONVERT_RESPONSE>";
        String msg = "";

        if (checkMsg == null) {
            Iterator iterator = map.values().iterator();
            msg += "<FILE_LIST>";
            while (iterator.hasNext()) {

                Map m = (Map) iterator.next();
                String code = (String) m.get("RET_CODE");
                if ("0".equals(code)) {
                    // 文件转换格式失败，RET_MSG为null
                    msg += "<TREE_NODE><RET_CODE>" + m.get("RET_CODE") + "</RET_CODE>" + "<FILE_NO>" + m.get("FILE_NO")
                            + "</FILE_NO>" + "<RET_MSG>" + m.get("FILE_MSG") + "</RET_MSG></TREE_NODE>";
                } else if ("1".equals(code)) {//文件转换成功
                    String fileUrl = "http://" + request.getLocalAddr() + ":" + request.getLocalPort() + "" + Util.getSystemDictionary("server.contextPath") + "/file";
                    String file = m.get("FILE_MSG").toString();
                    String folderTemp = file.replace(Util.getSystemDictionary("upload_path"), "");
                    // 生成的文件名称
                    String filename = folderTemp.substring(folderTemp.lastIndexOf("/") + 1);
                    // 文件目录
                    String folder = folderTemp.replace(filename, "");
                    msg += "<TREE_NODE>" +
                            "<RET_CODE>" + m.get("RET_CODE") + "</RET_CODE>" +
                            "<FILE_NO>" + m.get("FILE_NO") + "</FILE_NO>" +
                            "<FILE_URL>" + fileUrl + folder + "?name=" + filename + "</FILE_URL>" +
                            "</TREE_NODE>";
                }

            }
            msg += "</FILE_LIST>";
        } else {
            msg += "<RET_CODE>0</RET_CODE>" +
                    "<RET_MSG>" + checkMsg + "</RET_MSG>";
        }
        retXml += msg
                + "</FILE_CONVERT_RESPONSE>";
        return retXml;
    }

    /**
     * 根据传入社会信用代码，下载印章、证书信息
     *
     * @param xmlStr    请求报文
     * @param beginTime 开始时间
     * @param request
     * @return
     */
    public String sealCertData(String xmlStr, String beginTime, HttpServletRequest request) {
        init();
        /*用于封装返回报文信息*/
        String returnXml = "";
        Map retMap = new HashMap();
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        try {
            /*获取报文信息*/
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            Check check = new Check(beginTime);
            if (!check.checkAdh(sealDocRequest, request)) {
                log.info(beginTime + ":xml格式判断:" + check.getError());
                returnXml = getVarifyDataReturnXml(null, beginTime, check.getError());
            } else {
                Element SEAL_LIST = sealDocRequest.element("SEAL_LIST");
                List<Element> TREE_NODES = SEAL_LIST.elements("TREE_NODE");
                //保存文件,
                for (int i = 0; i < TREE_NODES.size(); i++) {
                    Element TREE_NODE = TREE_NODES.get(i);
                    Map map = new HashMap();
                    String unitCode = TREE_NODE.elementText("UNIT_CODE");
                    List<Map<String, Object>> seals = sealDao.getSealByUnitCode(unitCode);
                    Map sealsMap = new HashMap();
                    for (int j = 0; j < seals.size(); j++) {
                        Map<String, Object> seal = seals.get(j);
                    /*for (Map.Entry<String, Object> entry : seal.entrySet()) {
                        System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
                    }*/
                        sealsMap.put(j, seal);
                    }
                    retMap.put(i, sealsMap);
                }

                returnXml = getSealCertReturnXml(retMap, beginTime);
            }

        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getSealCertReturnXml(null, beginTime, "xml解析失败");
        } catch (Exception e) {
            e.printStackTrace();
            return getSealCertReturnXml(null, beginTime, e.getMessage());
        } finally {
            //log.info("saveFile文档关闭");
        }
        return returnXml;
    }

    /**
     * 下载印章、证书信息用
     *
     * @param map
     * @param beginTime
     * @return 响应报文
     */
    private String getSealCertReturnXml(Map map, String beginTime, String... checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<SEAL_INFOR_RESPONSE>";
        String msg = "";
        if (checkMsg.length == 0) {
            Iterator iterator = map.values().iterator();
            msg += "<SEAL_LIST>";
            while (iterator.hasNext()) {
                Map m = (Map) iterator.next();
                msg += "<TREE_NODE>";
                Iterator iterator2 = m.values().iterator();
                while (iterator2.hasNext()) {
                    Map m2 = (Map) iterator2.next();
                    Long certBegin = 0L;
                    if (m2.containsKey("certBeginTime") && m2.get("certBeginTime") != null && m2.get("certBeginTime") != "") {
                        certBegin = Long.parseLong(m2.get("certBeginTime").toString()) * 1000;
                    } else {
                        certBegin = null;
                    }

                    Long certEnd = 0L;
                    if (m2.containsKey("certEndTime") && m2.get("certEndTime") != null && m2.get("certEndTime") != "") {
                        certEnd = Long.parseLong(m2.get("certEndTime").toString()) * 1000;
                    } else {
                        certEnd = null;
                    }
                    msg += "<SEAL><SEAL_CODE>" + m2.get("sealCode") + "</SEAL_CODE>" + "<SEALID_FROMFABU>" + m2.get("sealId") + "</SEALID_FROMFABU>" + "<SEALID_FROMFABU_OLD>" + m2.get("oldSealId") + "</SEALID_FROMFABU_OLD>" + "<SEAL_NAME>" + m2.get("sealName") + "</SEAL_NAME>" + "<SEAL_STATUS>" + m2.get("status") + "</SEAL_STATUS>" + "<PREVIEW_DATA>" + m2.get("previewData") + "</PREVIEW_DATA>"
                            + "<ORDER_DEPT_NAME>" + m2.get("orderDeptName") + "</ORDER_DEPT_NAME>" + "<UNIT_CODE>" + m2.get("unitCode") + "</UNIT_CODE>" + "<UNIT_NAME>" + m2.get("unitName") + "</UNIT_NAME>"
                            + "<SEAL_HEIGHT>" + m2.get("sealHeight") + "</SEAL_HEIGHT>" + "<SEAL_WIDTH>" + m2.get("sealWidth") + "</SEAL_WIDTH>"
                            + "<SEAL_BEGIN_TIME>" + m2.get("sealBeginTime") + "</SEAL_BEGIN_TIME>" + "<SEAL_END_TIME>" + m2.get("sealEndTime") + "</SEAL_END_TIME>" + "</SEAL>"
                            //+ "<SEAL_DATA>" + m2.get("data") + "</SEAL_DATA>"
                            //cert
                            + "<SEAL_CERT>"
                            + "<CERT_NAME>" + m2.get("certName") + "</CERT_NAME>"
                            + "<CERT_ALIAS_NAME>" + m2.get("certAliasName") + "</CERT_ALIAS_NAME>" + "<CERT_BEGIN_TIME>" + certBegin + "</CERT_BEGIN_TIME>"
                            + "<CERT_END_TIME>" + certEnd + "</CERT_END_TIME>" + "<CERT_RET_USER>" + m2.get("regUser") + "</CERT_RET_USER>"
                            + "<CERT_SN>" + m2.get("certSn") + "</CERT_SN>" + "<CERT_DN>" + m2.get("certDn") + "</CERT_DN>" + "<CERT_CONTENT>" + m2.get("pfxContent") + "</CERT_CONTENT>"
                            + "</SEAL_CERT>";
                }
                msg += "</TREE_NODE>";
            }
        } else {
            msg += "<SEAL_LIST><RET_CODE>" + 0 + "</RET_CODE>" + "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "</SEAL_LIST>";
        }
        msg += "</SEAL_LIST>";
        retXml += msg
                + "</SEAL_INFOR_RESPONSE>";
        return retXml;

    }

    /**
     * 数据签名
     */
    public String signData(String xmlStr, String beginTime, HttpServletRequest request) {
        String returnXml = "";
        Map retMap = new HashMap();
        DocumentCreating documentCreating = (DocumentCreating) Util.getBean("documentCreating");
        try {
            /*获取报文信息*/
            Document doc = DocumentHelper.parseText(xmlStr);
            Element dataDocRequest = doc.getRootElement();
            Check check = new Check(beginTime);

            if (!check.checkAdh(dataDocRequest, request)) {
                log.info(beginTime + ":xml格式判断:" + check.getError());
                returnXml = getVarifyDataReturnXml(null, beginTime, check.getError());
            } else {
                Element DATA_LIST = dataDocRequest.element("DATA_LIST");
                List<Element> TREE_NODES = DATA_LIST.elements("TREE_NODE");
                for (int i = 0; i < TREE_NODES.size(); i++) {
                    Element TREE_NODE = TREE_NODES.get(i);
                    Map map = new HashMap();
                    String signData = TREE_NODE.elementText("WAIT_SIGN_DATA");
                    String ailasName = TREE_NODE.elementText("SM2_ALIAS_NAME");
                    Cert sm2Cert = certDao.findOneByAliasName(ailasName);
                    if (sm2Cert == null) {
                        map.put("retCode", "0");
                        map.put("msg", "对不起，找不到此证书");
                    } else {
                        map.put("WAIT_SIGN_DATA", signData);
                        documentCreating.signData(sm2Cert, Base64.encodeBase64(signData.getBytes("UTF-8")), new Date().getTime() + "", map);
                    }
                    retMap.put(i, map);
                }
                returnXml = getSignDataReturnXml(retMap, beginTime);
            }
        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getSignDataReturnXml(null, beginTime, "xml解析失败");
        } catch (Exception e) {
            e.printStackTrace();
            return getSignDataReturnXml(null, beginTime, e.getMessage());
        }
        return returnXml;
    }


    /**
     * 数据验签
     */
    public String varifyData(String xmlStr, String beginTime, HttpServletRequest request) {
        String returnXml = "";
        Map retMap = new HashMap();
        try {
            /*获取报文信息*/
            Document doc = DocumentHelper.parseText(xmlStr);
            Element dataRequest = doc.getRootElement();
            Check check = new Check(beginTime);
            if (!check.checkAdh(dataRequest, request)) {
                log.info(beginTime + ":xml格式判断:" + check.getError());
                returnXml = getVarifyDataReturnXml(null, beginTime, check.getError());
            } else {
                Element DATA_LIST = dataRequest.element("DATA_LIST");
                List<Element> TREE_NODES = DATA_LIST.elements("TREE_NODE");
                for (int i = 0; i < TREE_NODES.size(); i++) {
                    Element TREE_NODE = TREE_NODES.get(i);
                    Map map = new HashMap();
                    String sm2Ailas = TREE_NODE.elementText("SM2_ALIAS_NAME");
                    String originalData = TREE_NODE.elementText("ORIGINAL_DATA");
                    String signData = TREE_NODE.elementText("SIGN_DATA");
                    Cert sm2Cert = certDao.findOneByAliasName(sm2Ailas);
                    if (sm2Cert == null) {
                        log.info(beginTime + "对不起，找不到此证书");
                        map.put("ret_code", "0");
                        map.put("msg", "对不起，找不到此证书");
                    } else {
                        EncryptionDevice encryptionDevice = null;
                        try {
                            encryptionDevice = (EncryptionDevice) Util.getBean(Util.getSystemDictionary("encryptionDeviceImpl"));
                        } catch (Exception e) {
                            log.info(beginTime + "未找到签名实现:");
                            map.put("ret_code", "0");
                            map.put("msg", "未找到签名实现:");
                        }
                        boolean verifyRet = encryptionDevice.verifyP1(originalData.getBytes("UTF-8"), Base64.decodeBase64(sm2Cert.getPfxContent()), Base64.decodeBase64(signData));
                        if (!verifyRet) {
                            log.info(beginTime + "数据验证失败");
                            map.put("ret_code", "0");
                            map.put("msg", "数据验证失败");
                        } else {
                            log.info(beginTime + "数据验证成功");
                            map.put("ret_code", "1");
                            map.put("msg", "数据验证成功");
                        }
                        map.put("originalData", originalData);
                    }
                    retMap.put(i, map);
                }
                returnXml = getVarifyDataReturnXml(retMap, beginTime);
            }
        } catch (DocumentException e) {
            log.info(beginTime + "");
            e.printStackTrace();
            return getVarifyDataReturnXml(null, beginTime, "xml解析失败");
        } catch (Exception e) {
            e.printStackTrace();
            return getVarifyDataReturnXml(null, beginTime, e.getMessage());
        }
        return returnXml;
    }


    /**
     * 数据签名用返回报文
     *
     * @param map
     * @param beginTime
     * @param checkMsg
     * @return
     */
    private String getSignDataReturnXml(Map map, String beginTime, String... checkMsg) {

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<DATA_SIGN_RESPONSE>";
        String msg = "";
        if (checkMsg.length == 0) {
            Iterator iterator = map.values().iterator();
            msg += "<DATA_LIST>";
            while (iterator.hasNext()) {
                Map m = (Map) iterator.next();
                if ("0".equals(m.get("retCode"))) {
                    msg += "<TREE_NODE><RET_CODE>0</RET_CODE><RET_MSG>" + m.get("msg") + "</RET_MSG>";
                } else {
                    msg += "<TREE_NODE><RET_CODE>1</RET_CODE><ORIGINAL_DATA>" + m.get("WAIT_SIGN_DATA") + "</ORIGINAL_DATA><SIGN_DATA>" + m.get("signData") + "</SIGN_DATA>";
                }
                msg += "</TREE_NODE>";
            }
        } else {
            msg += "<DATA_LIST><RET_CODE>" + 0 + "</RET_CODE>" + "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "</DATA_LIST>";
        }
        msg += "</DATA_LIST>";
        retXml += msg + "</DATA_SIGN_RESPONSE>";
        return retXml;
    }

    /**
     * 数据验签用返回报文
     *
     * @param map
     * @param beginTime
     * @param checkMsg
     * @return
     */
    private String getVarifyDataReturnXml(Map map, String beginTime, String... checkMsg) {

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<DATA_VARIFY_RESPONSE>";
        String msg = "";
        if (checkMsg.length == 0) {
            Iterator iterator = map.values().iterator();
            msg += "<DATA_LIST>";
            while (iterator.hasNext()) {
                Map m = (Map) iterator.next();
                msg += "<TREE_NODE><RET_CODE>" + m.get("ret_code") + "</RET_CODE><RET_MSG>" + m.get("msg") + "</RET_MSG><ORIGINAL_DATA>" + m.get("originalData") + "</ORIGINAL_DATA>";
                msg += "</TREE_NODE>";
            }
        } else {
            msg += "<DATA_LIST><RET_CODE>" + 0 + "</RET_CODE>" + "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "</DATA_LIST>";
        }
        msg += "</DATA_LIST>";
        retXml += msg + "</DATA_VARIFY_RESPONSE>";
        return retXml;
    }

    /**
     * 文件转图片
     *
     * @param xmlStr
     * @param fileId
     * @param request
     * @return
     */
    public String fileToPicture(String xmlStr, String fileId, HttpServletRequest request) {
        init();//
        Document doc;
        long beginTime = System.currentTimeMillis();
        String FTP_ADDRESS = null;
        String FTP_PORT = null;
        String FTP_USER = null;
        String FTP_PWD = null;
        try {
            varifySystemType();
            doc = DocumentHelper.parseText(xmlStr);
            Element fileToPictureRequest = doc.getRootElement();
            FileToPictureCheck fileToPictureCheck = new FileToPictureCheck(fileId + "");
            if (!fileToPictureCheck.fileToPictureCheck(fileToPictureRequest, request)) {
                log.info(fileId + ":" + fileToPictureCheck.getError());
                return getFileToPictureRetXml(0, fileToPictureCheck.getError(), null, System.currentTimeMillis() - beginTime, 0);

            }
            //获取扩展信息META_DATA
            Element metaData = fileToPictureRequest.element("META_DATA");
            String FILE_NO = metaData.element("FILE_NO").getTextTrim();
            String FILE_PATH = metaData.element("FILE_PATH").getTextTrim();
            String FILE_TYPE = metaData.element("FILE_TYPE").getTextTrim();
            String PICTURE_TYPE = metaData.element("PICTURE_TYPE").getTextTrim();
            String PICTURE_WIDTH = metaData.element("PICTURE_WIDTH").getTextTrim();
            String MODE = metaData.element("MODE").getTextTrim();
            if (FILE_TYPE.equals("1")) {
                FTP_ADDRESS = metaData.elementText("FTP_ADDRESS");
                FTP_PORT = metaData.elementText("FTP_PORT");
                FTP_USER = metaData.elementText("FTP_USER");
                FTP_PWD = metaData.elementText("FTP_PWD");
            }
            Map<String, String> fileMsg = new HashMap<String, String>();
            String savePath = path + "/download/"; //下载文件存放路径
            Util.createDirs(savePath);
            String FileDownRet = null;
            log.info("FILE_TYPE:" + FILE_TYPE);
            if ("base64".equals(FILE_TYPE)) {

                /*3.base64方式传入*/
                String fileSuffix = FILE_NO.substring(FILE_NO.lastIndexOf(".") + 1);
                if (!"pdf".equalsIgnoreCase(fileSuffix) && !"ofd".equalsIgnoreCase(fileSuffix)) {
                    return getFileToPictureRetXml(0, "错误的文件名后缀FILE_NO", null, System.currentTimeMillis() - beginTime, 0);
                }
                String fname = fileId + "-" + new Date().getTime() + fileSuffix;
                String saveFilePath = Util.getSystemDictionary("upload_path") + "/downPathHttp/" + fname;

                Util.base64ToFile(FILE_PATH, saveFilePath);
                fileMsg.put("fileUrl", saveFilePath);
                fileMsg.put("fileName", fname);
                fileMsg.put("fileSuffix", fileSuffix);
                FileDownRet = "ok";
            } else if ("ftp".equals(FILE_TYPE)) {
                FileDownRet = SignatureFileUploadAndDownLoad.ftpDownFile1(null, FTP_ADDRESS, FTP_PORT, FTP_USER, FTP_PWD, FILE_PATH, fileId, fileMsg);
            } else {
                FileDownRet = SignatureFileUploadAndDownLoad.httpDownFile(FILE_PATH, beginTime + "", fileMsg, null);
            }
            if ("ok".equals(FileDownRet)) {
                String filePath = fileMsg.get("fileUrl");
                String dir = UUIDReduce.uuid();
                String imgFolder = fileToPicture + "img/" + dir + "/";
                Util.createFile(imgFolder);
                Map<String, Integer> m = new HashMap<String, Integer>();
                String pdfToImgAllPageRet = djPdfToImgUtil.pdfToImgAllPage(filePath, imgFolder, Integer.parseInt(PICTURE_WIDTH), PICTURE_TYPE, m);
                if ("success".equals(pdfToImgAllPageRet)) {
                    int pagenum = m.get("pagenum");
                    if (MODE.toLowerCase().equals("zip")) {
                        String zipPath = fileToPicture + "/zip/";
                        Util.createFile(imgFolder);
                        zipPath = zipPath + FILE_NO + ".zip";
                        Util.deleteFile(zipPath);
                        if (ZipUtil.zip(imgFolder, zipPath, false, null) != null) {
                            return getFileToPictureRetXml(1, "转换成功", "http://" + request.getLocalAddr() + ":" + request.getLocalPort() + Util.getSystemDictionary("server.contextPath") + "/file/fileToPicture/zip?name=" + FILE_NO + ".zip", System.currentTimeMillis() - beginTime, pagenum);
                        } else {
                            return getFileToPictureRetXml(0, "zip压缩失败", null, System.currentTimeMillis() - beginTime, pagenum);
                        }
                    } else if (MODE.toLowerCase().equals("show")) {
                        String ret = "<body  style='background-color:_CCCCCC;text-align:center'>";
                        for (int i = 1; i <= pagenum; i++) {
                            String fileUrl = Util.getSystemDictionary("server.contextPath") + "/file?name=fileToPicture_img_" + dir + "_" + i + "." + PICTURE_TYPE;
                            ret += "<img  style='margin-left:auto;margin-right:auto' width='" + PICTURE_WIDTH + "px' src='" + fileUrl + "'><br/><br/>";
                        }
                        ret = ret + "</body>";
                        return ret;
                    } else {
                        return getFileToPictureRetXml(1, "转换成功", "http://" + request.getLocalAddr() + ":" + request.getLocalPort() + Util.getSystemDictionary("server.contextPath") + "/file/fileToPicture/img/" + dir, System.currentTimeMillis() - beginTime, pagenum);
                    }
                } else {
                    return getFileToPictureRetXml(0, "转换失败:" + pdfToImgAllPageRet, null, System.currentTimeMillis() - beginTime, 0);
                }
            } else {
                return getFileToPictureRetXml(0, "文档下载失败", null, System.currentTimeMillis() - beginTime, 0);
            }

        } catch (DocumentException e) {
            return getFileToPictureRetXml(0, e.getMessage(), null, System.currentTimeMillis() - beginTime, 0);
        } catch (DJException e) {
            return getFileToPictureRetXml(0, e.getMessage(), null, System.currentTimeMillis() - beginTime, 0);
        } catch (Exception e) {
            return getFileToPictureRetXml(0, e.getMessage(), null, System.currentTimeMillis() - beginTime, 0);

        }
    }

    public String ofdToPicture(String ofdFilePath, String fileId, HttpServletRequest request) {
        long beginTime = System.currentTimeMillis();

        Map<String, String> fileMsg = new HashMap<String, String>();

        String savePath = Util.getSystemDictionary("upload_path") + "/pdfToImg/download/" + fileId + "/";
        Util.createDires(savePath);
        if (SignatureFileUploadAndDownLoad.httpDownFile(ofdFilePath, savePath, fileId + "", fileMsg, null).equals("ok")) {
            String filePath = fileMsg.get("fileUrl");
            String dir = UUID.randomUUID().toString();
            String imgFolder = Util.getSystemDictionary("upload_path") + "/pdfToImg/img/" + dir + "/";
            Util.createFile(imgFolder);
            Map<String, Integer> m = new HashMap<String, Integer>();
            String pdfToImgAllPageRet = djPdfToImgUtil.pdfToImgAllPage(filePath, imgFolder, Integer.parseInt("750"), "jpg", m);
            if ("success".equals(pdfToImgAllPageRet)) {
                int pagenum = m.get("pagenum");
                String ret = "<!DOCTYPE html>\n" +
                        "<html>\n" +
                        "<head>\n" +
                        "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"  />\n" +
                        "<meta http-equiv=\"Pragma\" content=\"no-cache\"/>\n" +
                        "<meta http-equiv=\"Expires\" content=\"0\"/>\n" +
                        "<meta http-equiv=\"Cache-Control\" content=\"no-cache\"/>\n" +
                        "<meta http-equiv=\"X-UA-Compatible\" />\n" +
                        "</head><body  style='background-color:#CCCCCC;text-align:center'>";
                for (int i = 1; i <= pagenum; i++) {
                    String fileUrl = "file/pdfToImg/img/" + dir + "?name=" + i + "." + "jpg";
                    ret += "<img  style='margin-left:auto;margin-right:auto' width='" + 750 + "px' src='" + fileUrl + "'><br/><br/>";
                }
                ret = ret + "</body></html>";
                return ret;
            } else {
                return getFileToPictureRetXml(0, "转换失败:" + pdfToImgAllPageRet, null, System.currentTimeMillis() - beginTime, 0);
            }
        } else {
            return getFileToPictureRetXml(0, "文档下载失败", null, System.currentTimeMillis() - beginTime, 0);
        }
    }

    private String getFileToPictureRetXml(int ret, String msg, String path, long switchTime, int pageCount) {
        String retStr = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><FILE_TO_PICTURE_RESPONSE><SWITCH_TIME>" + switchTime + "</SWITCH_TIME><RET_CODE>" + ret + "</RET_CODE><RET_MSG>" + msg + "</RET_MSG><PAGE_COUNT>" + pageCount + "</PAGE_COUNT><FILE_URL>" + path + "</FILE_URL></FILE_TO_PICTURE_RESPONSE>";
        return retStr;
    }

    public String fileToOnePicture(String xmlStr, String fileId, HttpServletRequest request) {
        log.info("报文内容为：：" + xmlStr);
        Document doc;
        long beginTime = System.currentTimeMillis();
        try {
            doc = DocumentHelper.parseText(xmlStr);
            Element fileToPictureRequest = doc.getRootElement();
            FileToPictureCheck fileToPictureCheck = new FileToPictureCheck(fileId + "");
            if (!fileToPictureCheck.fileToPictureCheck(fileToPictureRequest, request)) {
            } else {
                log.info(fileId + ":" + fileToPictureCheck.getError());
            }
            //获取扩展信息META_DATA
            Element metaData = fileToPictureRequest.element("META_DATA");
            String FILE_NO = metaData.element("FILE_NO").getTextTrim();
            String FILE_PATH = metaData.element("FILE_PATH").getTextTrim();
            String PICTURE_TYPE = metaData.element("PICTURE_TYPE").getTextTrim();
            String PICTURE_WIDTH = metaData.element("PICTURE_WIDTH").getTextTrim();
            String MODE = metaData.element("MODE").getTextTrim();
            Map<String, String> fileMsg = new HashMap<String, String>();

            String savePath = Util.getSystemDictionary("upload_path") + "/pdfToImg/download/" + FILE_NO;
            Util.createDires(savePath);
            if (SignatureFileUploadAndDownLoad.httpDownFile(FILE_PATH, savePath, fileId + "", fileMsg, null).equals("ok")) {
                String filePath = fileMsg.get("fileUrl");
                String dir = UUID.randomUUID().toString();
                String imgFolder = Util.getSystemDictionary("upload_path") + "/pdfToImg/img/" + dir + "/";
                Util.createFile(imgFolder);
                Map<String, Integer> m = new HashMap<String, Integer>();
                String pdfToImgAllPageRet = djPdfToImgUtil.pdfToOneImgAllPage(filePath, imgFolder, FILE_NO, PICTURE_TYPE, m);
                if ("success".equals(pdfToImgAllPageRet)) {
                    return getFileToPictureRetXml(1, "转换成功", "http://" + request.getLocalAddr() + ":" + request.getLocalPort() + Util.getSystemDictionary("server.contextPath") + "/file/pdfToImg/img/" + dir + "?name=" + FILE_NO, System.currentTimeMillis() - beginTime, 0);
                } else {
                    return getFileToPictureRetXml(0, "转换失败:" + pdfToImgAllPageRet, null, System.currentTimeMillis() - beginTime, 0);
                }


            } else {
                return getFileToPictureRetXml(0, "文档下载失败", null, System.currentTimeMillis() - beginTime, 0);
            }

        } catch (DocumentException e) {
            return getFileToPictureRetXml(0, e.getMessage(), null, System.currentTimeMillis() - beginTime, 0);
        }
    }


    public void initDir() {
        //创建所有服务端签章需要的文件夹(如果没有则创建)
        String upload_path = Util.getSystemDictionary("upload_path") + "/";
        String downPathFtp = Util.getSystemDictionary("downPathFtp");
        String downPathHttp = Util.getSystemDictionary("downPathHttp");
        String filePath = Util.getSystemDictionary("filePath");
        String sealFilePath = Util.getSystemDictionary("sealFilePath");
        String templateSynthesis = Util.getSystemDictionary("templateSynthesis");
        Util.createDires(upload_path + downPathFtp);
        Util.createDires(upload_path + downPathHttp);
        Util.createDires(upload_path + filePath);
        Util.createDires(upload_path + sealFilePath);
        Util.createDires(upload_path + templateSynthesis);
    }

    public String downloadSignDoc(String retXmlType, String xmlStr, SyntheticPattern syntheticPattern, String fileId, HttpServletRequest request) {
        long beginTime = System.currentTimeMillis();
/*        LogServerSeal logServerSeal = new LogServerSeal();
        logServerSeal.setId(fileId);
        logServerSeal.setRequestXml(xmlStr);*/
        String returnXml = "";

        try {
            varifySystemType();//验证授权
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            DownLoadDocCheck check = new DownLoadDocCheck(fileId + "");
/*
            logServerSeal.setCreatedAt(Util.getTimeStampOfNow());
*/
            //XML格式与内容判断
            if (!check.downLoadDoc(sealDocRequest, request)) {
                log.info(fileId + ":xml格式判断:" + check.getError());
/*
                logServerSeal.setResult(0);
*/
                returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, check.getError());
            } else {
                log.info(fileId + ":xml格式判断:成功");
/*
                logServerSeal.setResult(1);
*/
                List<Map<String, String>> sealList = new ArrayList<>();

                Map sealAutoDocRet = new HashMap();

                sealAutoDocRet = sealAutoDoc(sealDocRequest, syntheticPattern, fileId, sealList);
                returnXml = getReturnXml(retXmlType, sealAutoDocRet, "sealFilePath", beginTime, syntheticPattern);

                //记录日志
                Map<String, String> map = check.params;
                LogFileServerSeal logFileServerSeal = new LogFileServerSeal();
                logFileServerSeal.setSystemId(map.get("SYS_ID"));
                logFileServerSeal.setIpAddress(request.getRemoteAddr());
/*
                logFileServerSeal.setLogServerSealId(logServerSeal.getId());
*/
                logFileServerSeal.setDescription("签章添加水印");

                Iterator iterator = sealAutoDocRet.values().iterator();
                while (iterator.hasNext()) {
                    Map m = (Map) iterator.next();
/*                    if("0".equals(m.get("RET_CODE"))){
                        logServerSeal.setResult(0);
                    }*/
                    logFileServerSeal.setResult(Integer.parseInt(m.get("RET_CODE") + ""));
                    logFileServerSeal.setDocumentName((String) m.get("REQUEST_FILE_NO"));
                    logFileServerSealDao.save(logFileServerSeal);
                }
            }
        } catch (DocumentException e) {
            log.error(fileId + "");
            e.printStackTrace();
            returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, "xml解析失败");
        } catch (Exception e) {
            log.error(fileId + "");
            e.printStackTrace();
            returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, "签章文件加入水印失败" + e.getMessage());
        } finally {
     /*       logServerSeal.setResponseXml(returnXml);
            logServerSealDao.save(logServerSeal);*/
            return returnXml;
        }
    }

    /**
     * 删章添加水印加印章
     *
     * @param sealDocRequest
     * @param syntheticPattern
     * @param fileId
     * @return
     */
    private Map sealAutoDoc(Element sealDocRequest, SyntheticPattern syntheticPattern, String fileId, List<Map<String, String>> sealList) throws Exception {
        init();
        initDir();
        String suffixFolder = Util.getYYYYMMDDHH();
        Element FILE_LIST = sealDocRequest.element("FILE_LIST");
        List<Element> TREE_NODES = FILE_LIST.elements("TREE_NODE");
        Map msgMap = new HashMap<Integer, Map<String, String>>();
        SignatureFileUploadAndDownLoad.downFile(TREE_NODES, fileId, msgMap, Pattern.Next);
        documentCreating.deleteSealAddWaterMarker(TREE_NODES, fileId, msgMap, sealList);
        for (int i = 0; i < TREE_NODES.size(); i++) {
            String syntheticType = "ofd";
            Map thisMsg = (Map) msgMap.get(i);
            thisMsg.put("SUFFIX_FOLDER", suffixFolder);
            Element TREE_NODE = TREE_NODES.get(i);
            thisMsg.put("REQUEST_FILE_NO", thisMsg.get("FILE_NO"));
            Element RET_FILE_TYPE = TREE_NODE.element("RET_FILE_TYPE");
            if (RET_FILE_TYPE != null) {
                thisMsg.put("RET_FILE_TYPE", RET_FILE_TYPE.getTextTrim().toUpperCase());
            }
            int ret = 0;
            if ("1".equals(thisMsg.get("RET_CODE") + "")) {
                String filePath = (String) thisMsg.get("FILE_MSG");
                String fileNo = (String) thisMsg.get("FILE_NO");
                if (syntheticPattern == SyntheticPattern.AddSeal) {
                    String sfp = Util.getSuffixFolder(sealFilePath, suffixFolder) + fileNo + "." + syntheticType;
                    String addSealret = documentCreating.addOriginalSeal(filePath, sfp, TREE_NODE, fileNo, syntheticType, sealList);
                    if (!"ok".equals(addSealret)) {
                        /*失败但生成了盖章文件，需要删除,一般在上传盖章日志时生成*/
                        Util.deleteFile(sfp);
                        thisMsg.put("RET_CODE", "0");
                        thisMsg.put("FILE_MSG", addSealret);
                    } else {
                        thisMsg.put("FILE_MSG", sfp);
                        // thisMsg.put("FILE_NO",SEAL_FILE_PATH+"_"+ thisMsg.get("SUFFIX_FOLDER")+"_"+thisMsg.get("FILE_NO")+"."+syntheticType);
                        thisMsg.put("FILE_NO", thisMsg.get("FILE_NO") + "." + syntheticType);
                        ret = 1;
                    }
                }
                if (ret == 1) {
                    if (TREE_NODE.elementText("FTP_SAVEPATH") != null && !"".equals(TREE_NODE.elementText("FTP_SAVEPATH"))) {
                        String ftpUpFileRet = SignatureFileUploadAndDownLoad.ftpUpFile(TREE_NODE, thisMsg.get("FILE_MSG") + "", thisMsg.get("REQUEST_FILE_NO") + "");
                        if ("ok".equals(ftpUpFileRet)) {
                            thisMsg.put("RET_CODE", "1");
                            thisMsg.put("FILE_MSG", "文档上传成功");
                        } else {
                            thisMsg.put("RET_CODE", "0");
                            thisMsg.put("FILE_MSG", ftpUpFileRet);
                        }

                    } else {
                        thisMsg.put("RET_CODE", "1");
                        thisMsg.put("FILE_MSG", "文档合成成功");
                    }
                }
            }
            thisMsg.put("FILE_RETURN_TYPE", TREE_NODE.elementText("FILE_RETURN_TYPE"));
            if (TREE_NODE.elementText("FILE_RETURN_PATH") != null) {
                thisMsg.put("FILE_RETURN_PATH", TREE_NODE.elementText("FILE_RETURN_PATH"));
            }

        }
        return msgMap;
    }


    /**
     * 文档合成接口
     *
     * @param xmlStr           请求报文
     * @param syntheticPattern 类型
     * @param fileId
     * @param request
     * @return 响应报文
     */
    public String docSign(String retXmlType, String xmlStr, SyntheticPattern syntheticPattern, String fileId, HttpServletRequest request) {
        //创建所有服务端签章需要的文件夹(如果没有则创建)
        initDir();
        long beginTime = System.currentTimeMillis();
        String returnXml = "";

        try {
            varifySystemType();
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            SealAutoPdfCheckZF check = new SealAutoPdfCheckZF(fileId + "", syntheticPattern);//与ofd平台不一样
            log.info(fileId + ":xml格式判断:成功");
            Element META_DATA = sealDocRequest.element("META_DATA");
            String IS_MERGER = META_DATA.elementText("IS_MERGER");
            Map sealAutoPdfForMerageRet = new HashMap();
            if ("true".equals(IS_MERGER)) {
                sealAutoPdfForMerageRet = sealAutoPdfForMerageZF(sealDocRequest, syntheticPattern, fileId);
                returnXml = getReturnXml(retXmlType, sealAutoPdfForMerageRet, SEAL_FILE_PATH, beginTime, syntheticPattern);
            } else {
                sealAutoPdfForMerageRet = sealAutoPdfForNotMergeZF(sealDocRequest, syntheticPattern, fileId);
                returnXml = getReturnXml(retXmlType, sealAutoPdfForMerageRet, SEAL_FILE_PATH, beginTime, syntheticPattern);
            }

        } catch (DocumentException e) {
            log.error(fileId + "");
            e.printStackTrace();
            returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, "xml解析失败");
        } catch (Exception e) {
            log.error(fileId + "");
            e.printStackTrace();
            returnXml = getReturnXml(retXmlType, null, "", beginTime, syntheticPattern, "模版合成与盖章失败" + e.getMessage());
        } finally {
            return returnXml;
        }
    }

    @Autowired
    private DocumentCreating documentCreating;
    @Autowired
    private SrvSealUtil srvSealUtil;
    /*  private String path = null;
    private String filePath = null;
  private String sealFilePath = null;*/
    private String syntheticType = null;
    private Map<String, String> documentInfo = new HashMap<>();//文档信息
    //@Value("${server.contextPath}")
    private String contextPath = "/";

    protected final String FILE_PATH = "filePath";
    protected final String SEAL_FILE_PATH = "sealFilePath";
    protected String path = null;
    protected String filePath = null;
    protected String sealFilePath = null;
    //	protected String syntheticType = null;
    protected String htmlPath = null;
    protected String htmltoPdfPath = null;
    protected String pdfToImg = null;
    protected String pdfToOfd = null;
    protected String downPathHttp = null;
    protected String downPathFtp = null;
    protected String templateSynthesis = null;
    protected String fileToPicture = null;

    @Autowired
    private LogServerSealDao logServerSealDao;
    @Autowired
    private LogFileServerSealDao logFileServerSealDao;
    @Autowired
    private SealDao sealDao;
    @Autowired
    private DocumentDao documentDao;

    @Autowired
    private CertDao certDao;

    @Autowired
    private DJPdfToImgUtil djPdfToImgUtil;

    private Map getSealInformation(int nObjID, String nodeName) {
        Map m = new HashMap();
        //证书
        String certData = srvSealUtil.getValueEx(nObjID, nodeName, 39, "", 0, "");
        //
        String ofdsign = srvSealUtil.getValueEx(nObjID, nodeName, 56, "", 0, "");
        //签名原文
        String ofdoridata = srvSealUtil.getValueEx(nObjID, nodeName, 57, "", 0, "");
        //印章编码
        String sealCode = srvSealUtil.getValueEx(nObjID, nodeName, 32, "", 0, "");//电子印章编码
        //印章名称
        String sealName = srvSealUtil.getValueEx(nObjID, nodeName, 33, "", 0, "");//电子印章编码
        // 印章数据
        String sealData = srvSealUtil.getValueEx(nObjID, nodeName, 59, "", 0, "");

        /*
        从证书数据里获取sn,dn
        */
        if (StringUtils.isNotEmpty(certData)) {
            ASN1InputStream aIn = null;
            InputStream in = null;
            try {
                in = new ByteArrayInputStream(Base64.decodeBase64(certData));
                aIn = new ASN1InputStream(in);
                ASN1Sequence seq = null;
                seq = (ASN1Sequence) aIn.readObject();
                X509CertificateStructure sm2pubkey = new X509CertificateStructure(seq);
                m.put("sn", sm2pubkey.getSerialNumber().toString());
                m.put("dn", sm2pubkey.getSubject().toString().replace("GIVENNAME", "G").replace("ST", "S"));
            } catch (Exception e) {
                try {
                    aIn.close();
                    in.close();
                } catch (Exception e1) {

                }
            }
        }
        log.info("sealCode=" + sealCode);
        m.put("nodeName", nodeName);
        m.put("certData", certData);
        m.put("ofdsign", ofdsign);
        m.put("ofdoridata", ofdoridata);
        m.put("sealCode", sealCode);
        m.put("sealName", sealName);
        m.put("sealData", sealData);

        return m;
    }

    /**
     * 链接发布系统验证印章有效性
     *
     * @param sealName
     * @param sealCode
     * @return
     */
    private JSONObject verifySealStatus(String sealName, String sealCode) {
        JSONObject object = new JSONObject();
        object.put("sealStatus", "0");
        String publishSystem = Util.getSystemDictionary("publishSystem");
        /*查询印章状态,110是空白印章的sealCode*/
        if ("1".equals(publishSystem) && !"110".equals(sealCode)) {
            /*适配河北老数据,老数据不链接发布系统验证印章状态*/
            if (sealCode.length() != 14 && !sealCode.startsWith("130000")) {
            } else {
                GetSealStatusRequestData requestData = new GetSealStatusRequestData(sealName, sealCode, Util.sdf3.format(new Date()));
                object = SealUtilTool.getSealStatus(requestData);
                return object;
            }
        }
        return object;
    }

    public String getSealInformation(String xmlStr, String filed, HttpServletRequest request) {
        String returnXml = "";
        Map retMap = new HashMap();

        try {
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            Check check = new Check(filed);
            //XML格式与内容判断
            if (!check.checkAdh(sealDocRequest, request)) {
                log.info(":xml格式判断:" + check.getError());
                returnXml = getVarifyDataReturnXml(null, filed, check.getError());
            } else {
                Element META_DATA = sealDocRequest.element("META_DATA");
                if (META_DATA.element("BEGIN_TIME") == null) {
                    returnXml = getVarifyDataReturnXml(null, filed, "BEGIN_TIME不存在");
                }
                if (META_DATA.element("END_TIME") == null) {
                    returnXml = getVarifyDataReturnXml(null, filed, "END_TIME不存在");
                }
                if (META_DATA.element("SEAL_STATUS") == null) {
                    returnXml = getVarifyDataReturnXml(null, filed, "SEAL_STATUS不存在");
                }

                String beginTime = META_DATA.elementText("BEGIN_TIME");
                String endTime = META_DATA.elementText("END_TIME");
                String status = META_DATA.elementText("SEAL_STATUS");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                long beginTimeStamp = sdf.parse(beginTime).getTime() / 1000;
                long endTimeStamp = sdf.parse(endTime).getTime() / 1000;

                List<Map<String, Object>> seals = sealDao.getSealByTime(beginTimeStamp, endTimeStamp, status);
                for (int j = 0; j < seals.size(); j++) {
                    Map<String, Object> seal = seals.get(j);
                    retMap.put(j, seal);
                }
                returnXml = getSealInformationReturnXml(retMap, beginTime);
            }
        } catch (DocumentException e) {
            e.printStackTrace();
            returnXml = getSealInformationReturnXml(null, filed, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            returnXml = getSealInformationReturnXml(null, filed, e.getMessage());
        } finally {
            return returnXml;
        }
    }

    /**
     * 返回印章
     *
     * @param map
     * @param beginTime
     * @return 响应报文
     */
    private String getSealFromCertDnOrSnReturnXml(Map map, String beginTime, String... checkMsg) {
        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<SEAL_INFOR_RESPONSE>";
        String msg = "";
        if (checkMsg.length == 0) {
            Iterator iterator = map.values().iterator();
            msg += "<SEAL_LIST>";
            while (iterator.hasNext()) {
                Map m = (Map) iterator.next();
                msg += "<SEAL>" +
                        "<SEAL_NAME>" + m.get("sealName") + "</SEAL_NAME>" +
                        "<SEAL_WIDTH>" + m.get("sealWidth") + "</SEAL_WIDTH>" +
                        "<SEAL_HEIGHT>" + m.get("sealHeight") + "</SEAL_HEIGHT>" +
                        "<SEAL_DATA>" + m.get("previewData") + "</SEAL_DATA>" +
                        "</SEAL>";
            }
        } else {
            msg += "<SEAL_LIST><RET_CODE>" + 0 + "</RET_CODE>" + "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "</SEAL_LIST>";
        }
        msg += "</SEAL_LIST>";
        retXml += msg
                + "</SEAL_INFOR_RESPONSE>";
        return retXml;
    }

    public String getSealFromCertDnOrSn(String xmlStr, String filed, HttpServletRequest request) {
        String returnXml = "";
        Map retMap = new HashMap();

        try {
            Document doc = DocumentHelper.parseText(xmlStr);
            Element sealDocRequest = doc.getRootElement();
            Check check = new Check(filed);
            //XML格式与内容判断
            if (!check.checkAdh(sealDocRequest, request)) {
                log.info(":xml格式判断:" + check.getError());
                returnXml = getVarifyDataReturnXml(null, filed, check.getError());
            } else {
                Element META_DATA = sealDocRequest.element("META_DATA");
                if (META_DATA.element("CERT_SN") == null && META_DATA.element("CERT_DN") == null) {
                    returnXml = getVarifyDataReturnXml(null, filed, "CERT_SN或CERT_DN必须存在一个");
                }
                String dn = "";
                String sn = "";
                if (META_DATA.element("CERT_DN") != null) {
                    dn = META_DATA.elementText("CERT_DN");
                }
                if (META_DATA.element("CERT_SN") != null) {
                    sn = META_DATA.elementText("CERT_SN");
                }

                List<Map<String, Object>> seals = sealDao.getSealFromCertDnOrSn(dn, sn);
                for (int j = 0; j < seals.size(); j++) {
                    Map<String, Object> seal = seals.get(j);
                    retMap.put(j, seal);
                }
                returnXml = getSealFromCertDnOrSnReturnXml(retMap, "");
            }
        } catch (DocumentException e) {
            e.printStackTrace();
            returnXml = getSealFromCertDnOrSnReturnXml(null, filed, e.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            returnXml = getSealFromCertDnOrSnReturnXml(null, filed, e.getMessage());
        } finally {
            return returnXml;
        }
    }

    /**
     * 返回印章
     *
     * @param map
     * @param beginTime
     * @return 响应报文
     */
    private String getSealInformationReturnXml(Map map, String beginTime, String... checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                + "<SEAL_INFOR_RESPONSE>";
        String msg = "";
        if (checkMsg.length == 0) {
            Iterator iterator = map.values().iterator();
            msg += "<SEAL_LIST>";
            while (iterator.hasNext()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Map m = (Map) iterator.next();
                String createdAt = sdf.format(Long.parseLong(m.get("createdAt").toString()) * 1000);
                String updatedAt = sdf.format(Long.parseLong(m.get("updatedAt").toString()) * 1000);

                msg += "<SEAL>" +
                        "<SEAL_NAME>" + m.get("sealName") + "</SEAL_NAME>" +
                        "<SEAL_CODE>" + m.get("sealCode") + "</SEAL_CODE>" +
                        "<SEAL_CREATED_AT>" + createdAt + "</SEAL_CREATED_AT>" +
                        "<SEAL_UPDATED_AT>" + updatedAt + "</SEAL_UPDATED_AT>" +
                        "<UNIT_CODE>" + m.get("unitCode") + "</UNIT_CODE>" +
                        "<UNIT_NAME>" + m.get("unitName") + "</UNIT_NAME>" +
                        "<SEAL_STATUS>" + m.get("status") + "</SEAL_STATUS>" +
                        "</SEAL>";
            }
        } else {
            msg += "<SEAL_LIST><RET_CODE>" + 0 + "</RET_CODE>" + "<RET_MSG>" + checkMsg[0] + "</RET_MSG>"
                    + "</SEAL_LIST>";
        }
        msg += "</SEAL_LIST>";
        retXml += msg
                + "</SEAL_INFOR_RESPONSE>";
        return retXml;
    }

    public String jieXiXml(String retXmlType, String xmlStr, String fileId, HttpServletRequest request) {
        String returnXml = "";
        Map retMap = new HashMap();
        String fileName = fileId + ".ofd";

        try {
            Document doc = DocumentHelper.parseText(xmlStr);
            Element rootElement = doc.getRootElement();
            Check check = new Check(fileId);
            //XML格式与内容判断
            if (!check.checkAdh(rootElement, request)) {
                log.info(":xml格式判断:" + check.getError());
                returnXml = getVarifyDataReturnXml(null, fileId, check.getError());
            } else {
                log.info(fileId + ":xml格式判断:成功");
                DJFapiaoObj dJFapiaoObj = new DJFapiaoObj();
                ShouYePageContent shouYePageContent = new ShouYePageContent();//首页
                List<XuYePageContent> xuyepageContentList = null;//续页
                WeiYePageContent weiyePageContent = null;//尾页
                Element baseData = rootElement.element("BASE_DATA");
                String sysId = baseData.element("SYS_ID").getText();
                dJFapiaoObj.setSysId(sysId);
                Element eInvoice = rootElement.element("EINVOICE");
                //dJFapiaoObj.setOriginal_invoice(xmlStr);
                dJFapiaoObj.setOriginal_invoice(replaceXml(xmlStr));
                Element docIDElement= eInvoice.element("DocID");
                // String DocID = eInvoice.element("DocID").getText();//docid
                if(docIDElement!=null){
                    dJFapiaoObj.setDocID(docIDElement.getText());
                }
                String piaoTou = eInvoice.element("piaoTou").getText();//票头
                dJFapiaoObj.setPiaoTou(piaoTou);
                String inVoiceCode = "";//发票代码
                Element inVoiceCodeElement = eInvoice.element("InvoiceCode");//发票代码
                if(inVoiceCodeElement!=null){
                    inVoiceCode = inVoiceCodeElement.getText();
                    shouYePageContent.setInvoiceCode(inVoiceCode);
                }else{
                    return getElectronicReturnXml(null, "", "发票代码为空");
                }
                String inVoiceNo = eInvoice.element("InvoiceNo").getText();//发票号码
                shouYePageContent.setInvoiceNo(inVoiceNo);
                Element typeCodeElement= eInvoice.element("TypeCode");
                if(typeCodeElement!=null){
                    //String typeCode = typeCodeElement.getText();//
                    shouYePageContent.setTypeCode(typeCodeElement.getText());
                }else{
                    //走不下去了
                    log.info("缺少typeCode标签, 0-正数发票；1-负数发票 ");
                    returnXml =getElectronicReturnXml(null, "", "缺少typeCode标签, 0-正数发票；1-负数发票") ;
                    return getElectronicReturnXml(null, "", "缺少typeCode标签, 0-正数发票；1-负数发票");
                }
                /*String typeCode = eInvoice.element("TypeCode")==null?"" : eInvoice.element("TypeCode").getText();//
                shouYePageContent.setTypeCode(typeCode);*/
                String machineNo = eInvoice.element("MachineNo").getText();//机器编码
                shouYePageContent.setMachineNo(machineNo);
                String issueDate = eInvoice.element("IssueDate").getText();//开票日期
                shouYePageContent.setIssueDate(issueDate);
                String taxControlCode = eInvoice.element("TaxControlCode").getText();//密码区
                ///System.out.println(taxControlCode);
                taxControlCode = StringEscapeUtils.unescapeXml(taxControlCode);
                shouYePageContent.setTaxControlCode(taxControlCode);
                String invoiceCheckCode = eInvoice.element("InvoiceCheckCode").getText();//密码区
                shouYePageContent.setInvoiceCheckCode(invoiceCheckCode);
                String graphCode = eInvoice.element("GraphCode").getText();//密码区
                shouYePageContent.setGraphCode(graphCode);
                //销售方
                Element seller = eInvoice.element("Seller");
                String sellerName = seller.element("SellerName").getText();//名称
                shouYePageContent.setSellerName(sellerName);
                String sellerTaxId = seller.element("SellerTaxID").getText();//纳税人识别号
                shouYePageContent.setSellerTaxID(sellerTaxId);
                String sellerAddrTel = seller.element("SellerAddrTel").getText();//地址  电话
                shouYePageContent.setSellerAdderTel(sellerAddrTel);
                String sellerFinancialAccount = seller.element("SellerFinancialAccount").getText();//开户银行及账号
                shouYePageContent.setSellerFinancialAccount(sellerFinancialAccount);

                //购买方
                Element buyer = eInvoice.element("Buyer");
                String buyerName = buyer.element("BuyerName").getText();//名称
                shouYePageContent.setBuyerName(buyerName);
                String buyerTaxId = buyer.element("BuyerTaxID").getText();//纳税人识别号
                shouYePageContent.setBuyerTaxID(buyerTaxId);
                String buyerAddrTel = buyer.element("BuyerAddrTel").getText();//地址  电话
                shouYePageContent.setBuyerAdderTel(buyerAddrTel);
                String buyerFinancialAccount = buyer.element("BuyerFinancialAccount").getText();//开户银行及账号
                shouYePageContent.setBuyerFinancialAccount(buyerFinancialAccount);


                String payee = eInvoice.element("Payee").getText();//收款人
                shouYePageContent.setPayee(payee);
                String checker = eInvoice.element("Checker").getText();//复核人
                shouYePageContent.setChecker(checker);
                String invoiceClerk = eInvoice.element("InvoiceClerk").getText();//开票人
                shouYePageContent.setInvoiceClerk(invoiceClerk);

                //String taxInclusiveTotalAmount = eInvoice.element("TaxInclusiveTotalAmount");//价税合计
                String taxInclusiveTotalAmount="";
                Element taxInclusiveTotalAmountElement = eInvoice.element("TaxInclusiveTotalAmount");
                if(taxInclusiveTotalAmountElement!=null){
                    taxInclusiveTotalAmount  =taxInclusiveTotalAmountElement.getText();
                    shouYePageContent.setTaxInclusiveTotalAmount(taxInclusiveTotalAmountElement.getText());
                }
                String taxExclusiveTotalAmount="";
                Element taxExclusiveTotalAmountElement =  eInvoice.element("TaxExclusiveTotalAmount");
                if(taxExclusiveTotalAmountElement!=null){
                    taxExclusiveTotalAmount= taxExclusiveTotalAmountElement.getText();
                    shouYePageContent.setTaxExclusiveTotalAmount(taxExclusiveTotalAmountElement.getText());
                }
                //String taxExclusiveTotalAmount = eInvoice.element("TaxExclusiveTotalAmount").getText();//金额

                Element taxTotalAmountElement = eInvoice.element("TaxTotalAmount");
                //String taxTotalAmount = eInvoice.element("TaxTotalAmount").getText();//税额
                String taxTotalAmount="";
                if(taxTotalAmountElement!=null){
                    taxTotalAmount = taxTotalAmountElement.getText();
                    shouYePageContent.setTaxToTotalAmount(taxTotalAmountElement.getText());
                }
                String note = "";//备注
                Element noteElement= eInvoice.element("Note");
                if(noteElement!=null){
                    note = noteElement.getText();
                    shouYePageContent.setNote(note);
                }
                String invoiceSIA1 = eInvoice.element("InvoiceSIA1").getText();//InvoiceSIA1
                String invoiceSIA2 = eInvoice.element("InvoiceSIA2").getText();//InvoiceSIA2
                shouYePageContent.setInvoiceSIA1(invoiceSIA1);
                shouYePageContent.setInvoiceSIA2(invoiceSIA2);
                /***
                 * 如果商品的个数《=8    则不存在续页和尾页
                 *
                 */
                Element goodsInfos = eInvoice.element("GoodsInfos");
                List<Element> goodsInfoNodes = goodsInfos.elements("GoodsInfo");
                double   moneyAmount = 0;
                double   taxAmount1  = 0;
                String   mianShui = "";
                if(goodsInfoNodes.size()<=8) {
                    List<GoodInfo>   goodInfoList =  new ArrayList<GoodInfo>(goodsInfoNodes.size());
                    for(int i=0;i<goodsInfoNodes.size();i++){//获取某个清单
                        Element goodsInfoNode=goodsInfoNodes.get(i);
                        GoodInfo   goodInfo = new  GoodInfo();
                        String item=goodsInfoNode.elementText("Item");//项目名称
                        goodInfo.setItem(item);
                        String specification=goodsInfoNode.elementText("Specification");//规格型号
                        goodInfo.setSpecification(specification);
                        String measurementDimension=goodsInfoNode.elementText("MeasurementDimension");//单位
                        goodInfo.setMeasurementDimension(measurementDimension);
                        String quantity=goodsInfoNode.elementText("Quantity");//数量
                        if("0".equals(quantity)){
                            goodInfo.setQuantity("");
                        }else{
                            goodInfo.setQuantity(quantity);
                        }
                        String price=goodsInfoNode.elementText("Price");//单价
                        if("0".equals(price)){
                            goodInfo.setPrice("");
                        }else{
                            goodInfo.setPrice(price);
                        }

                        String amount=goodsInfoNode.elementText("Amount");//金额
                        goodInfo.setAmount(amount);
                        moneyAmount += Double.parseDouble(amount);
                        String taxScheme=goodsInfoNode.elementText("TaxScheme");//税率
                        goodInfo.setTaxScheme(taxScheme);
                        String taxAmount=goodsInfoNode.elementText("TaxAmount");//税额
                        if(taxAmount.indexOf("*")!=-1){
                            mianShui = "免税";
                        }else{
                            taxAmount1 += Double.parseDouble(taxAmount);
                        }
                        //taxAmount1 += Double.parseDouble(taxAmount);
                        goodInfo.setTaxAmount(taxAmount);
                        goodInfoList.add(goodInfo);
                        // String extention=goodsInfoNode.elementText("Extention");//序号
                    }
                    if(taxExclusiveTotalAmount==null||"".equals(taxExclusiveTotalAmount)) {
                        //shouYePageContent.setTaxExclusiveTotalAmount(Double.toString(moneyAmount));
                        shouYePageContent.setTaxExclusiveTotalAmount(baoliuliangwei(moneyAmount));
                    }
                    if(taxTotalAmount==null||"".equals(taxTotalAmount)) {
                        //shouYePageContent.setTaxToTotalAmount(Double.toString(taxAmount1));
                        shouYePageContent.setTaxToTotalAmount(baoliuliangwei(taxAmount1));
                    }
                    if(taxInclusiveTotalAmount==null||"".equals(taxInclusiveTotalAmount)) {
                        //shouYePageContent.setTaxInclusiveTotalAmount(Double.toString(moneyAmount+taxAmount1));
                        shouYePageContent.setTaxInclusiveTotalAmount(baoliuliangwei(moneyAmount+taxAmount1));
                        shouYePageContent.setTaxInclusiveTotalAmountofCHS(NumberToCN.number2CNMontrayUnit(new BigDecimal(moneyAmount+taxAmount1)));
                    }else{
                        shouYePageContent.setTaxInclusiveTotalAmountofCHS(NumberToCN.number2CNMontrayUnit(new BigDecimal(taxInclusiveTotalAmount)));
                    }
                    shouYePageContent.setGoodInfoList(goodInfoList);
                }else  if(goodsInfoNodes.size()>8) {//就有销货清单
                    //xuyepageContentList = new  ArrayList<XuYePageContent>();
                    List<GoodInfo>   shouyeInfoList =  new ArrayList<GoodInfo>();
                    GoodInfo   shouyegoodInfo = new  GoodInfo();
                    shouyegoodInfo.setItem("(详见销货清单)");
                    shouyegoodInfo.setAmount(taxExclusiveTotalAmount);
                    shouyegoodInfo.setTaxAmount(taxTotalAmount);
                    int   goodCount = goodsInfoNodes.size();
                    if(goodCount>26) {
                        xuyepageContentList = new  ArrayList<XuYePageContent>();
                        //尾页
                        weiyePageContent =  new  WeiYePageContent();
                        //只有当商品数量大于26时才有  续页和尾页同时存在
                        int   i = goodCount/26+(goodCount%26==0?0:1);//代表总共有多少页
                        int   s = 0;
                        double   moneyAmount3 = 0;
                        double   taxAmount3 = 0;
                        for(int j=1;j<=i;j++) {
                            if(j==i) {
                                //最终的尾页
                                weiyePageContent =  new  WeiYePageContent();
                                weiyePageContent.setBuyerName(buyerName);
                                weiyePageContent.setSellerName(sellerName);
                                weiyePageContent.setInvoiceCode(inVoiceCode);
                                weiyePageContent.setInvoiceNo(inVoiceNo);
                                //xuYePageContent.setSubtotalofAmount(subtotalofAmount);
                                //weiyePageContent.setSubtotalofAmount(taxExclusiveTotalAmount);(金额)小计
                                weiyePageContent.setSubtotalofTotal(taxExclusiveTotalAmount);
                                //weiyePageContent.setTaxAmountofSubtotal(taxTotalAmount);(税额)小计
                                weiyePageContent.setTaxAmountofTotal(taxTotalAmount);
                                weiyePageContent.setRemarks(note);
                                weiyePageContent.setDateofinvoice(issueDate);
                                weiyePageContent.setPageCount(j+"");
                                weiyePageContent.setPagePosition(j+"");
                                List<GoodInfo>   goodInfoList =  new ArrayList<GoodInfo>(goodCount%26);
                                double   weiyeMoneyAmount2 = 0;
                                double   weiyetaxAmount2 = 0;
                                for(int  a=s;a<goodsInfoNodes.size();a++) {
                                    Element goodsInfoNode=goodsInfoNodes.get(a);
                                    GoodInfo   goodInfo = new  GoodInfo();
                                    String item=goodsInfoNode.elementText("Item");//项目名称
                                    goodInfo.setItem(item);
                                    String specification=goodsInfoNode.elementText("Specification");//规格型号
                                    goodInfo.setSpecification(specification);
                                    String measurementDimension=goodsInfoNode.elementText("MeasurementDimension");//单位
                                    goodInfo.setMeasurementDimension(measurementDimension);
                                    String quantity=goodsInfoNode.elementText("Quantity");//数量
                                    if("0".equals(quantity)){
                                        goodInfo.setQuantity("");
                                    }else{
                                        goodInfo.setQuantity(quantity);
                                    }
                                    String price=goodsInfoNode.elementText("Price");//单价
                                    if("0".equals(price)){
                                        goodInfo.setPrice("");
                                    }else{
                                        goodInfo.setPrice(price);
                                    }
                                    String amount=goodsInfoNode.elementText("Amount");//金额
                                    weiyeMoneyAmount2 += Double.parseDouble(amount);//续页总计
                                    goodInfo.setAmount(amount);
                                    String taxScheme=goodsInfoNode.elementText("TaxScheme");//税率
                                    goodInfo.setTaxScheme(taxScheme);
                                    String taxAmount=goodsInfoNode.elementText("TaxAmount");//税额
                                    if(taxAmount.indexOf("*")!=-1){
                                        mianShui = "免税";
                                    }else{
                                        weiyetaxAmount2 += Double.parseDouble(taxAmount);
                                    }
                                    //weiyetaxAmount2 += Double.parseDouble(taxAmount);
                                    goodInfo.setTaxAmount(taxAmount);
                                    //String extention=goodsInfoNode.elementText("Extention");//序号
                                    goodInfo.setExtention_XH(Integer.toString(a+1));
                                    //goodInfo.setExtention_XH(extention);
                                    goodInfoList.add(goodInfo);
                                }
                                shouyegoodInfo.setTaxScheme(goodInfoList.get(0).getTaxScheme());
                                //添加尾页小计
                                if(taxTotalAmount.indexOf("*")!=-1){
                                    weiyePageContent.setTaxAmountofSubtotal(taxTotalAmount);
                                }else{
                                    weiyePageContent.setTaxAmountofSubtotal(baoliuliangwei(weiyetaxAmount2));
                                }
                                weiyePageContent.setSubtotalofAmount(baoliuliangwei(weiyeMoneyAmount2));
                                //添加首页尾页的总合计
                                moneyAmount3 += weiyeMoneyAmount2;
                                taxAmount3  += weiyetaxAmount2;
                                if(taxExclusiveTotalAmount==null||"".equals(taxExclusiveTotalAmount)||taxTotalAmount.indexOf("*")!=-1) {
                                    //shouYePageContent.setTaxExclusiveTotalAmount(Double.toString(moneyAmount3));  baoliuliangwei
                                    shouYePageContent.setTaxExclusiveTotalAmount(baoliuliangwei(moneyAmount3));
                                    //weiyePageContent.setSubtotalofTotal(Double.toString(moneyAmount3));
                                    weiyePageContent.setSubtotalofTotal(baoliuliangwei(moneyAmount3));
                                    //shouyegoodInfo.setAmount(Double.toString(moneyAmount3));
                                    shouyegoodInfo.setAmount(baoliuliangwei(moneyAmount3));
                                }
                                if(taxTotalAmount==null||"".equals(taxTotalAmount)) {
                                    shouYePageContent.setTaxToTotalAmount(baoliuliangwei(taxAmount3));
                                    weiyePageContent.setTaxAmountofTotal(baoliuliangwei(taxAmount3));
                                    shouyegoodInfo.setTaxAmount(baoliuliangwei(taxAmount3));
                                }else  if(taxTotalAmount.indexOf("*")!=-1){
                                    shouYePageContent.setTaxToTotalAmount(taxTotalAmount);
                                    weiyePageContent.setTaxAmountofTotal(taxTotalAmount);
                                    shouyegoodInfo.setTaxAmount(taxTotalAmount);
                                }
                                if(taxInclusiveTotalAmount==null||"".equals(taxInclusiveTotalAmount)||taxTotalAmount.indexOf("*")!=-1) {
                                    shouYePageContent.setTaxInclusiveTotalAmount(baoliuliangwei(moneyAmount3+taxAmount3));
                                    shouYePageContent.setTaxInclusiveTotalAmountofCHS(NumberToCN.number2CNMontrayUnit(new BigDecimal(moneyAmount3+taxAmount3)));
                                }else{
                                    shouYePageContent.setTaxInclusiveTotalAmountofCHS(NumberToCN.number2CNMontrayUnit(new BigDecimal(taxInclusiveTotalAmount)));
                                }

                                weiyePageContent.setGoodInfoList(goodInfoList);
                            }else {
                                XuYePageContent   xuYePageContent = new XuYePageContent();
                                xuYePageContent.setBuyerName(buyerName);
                                xuYePageContent.setSellerName(sellerName);
                                xuYePageContent.setInvoiceCode(inVoiceCode);
                                xuYePageContent.setInvoiceNo(inVoiceNo);
                                xuYePageContent.setRemarks(note);
                                xuYePageContent.setDateofinvoice(issueDate);
                                xuYePageContent.setPageCount(i+"");
                                xuYePageContent.setPagePosition(j+"");
                                double   xuyeMoneyAmount = 0;
                                double   xuyetaxAmount = 0;
                                //商品信息   确定有26条信息
                                List<GoodInfo>   goodInfoList =  new ArrayList<GoodInfo>(26);
                                for(int a=s;a<(s+26);a++) {
                                    Element goodsInfoNode=goodsInfoNodes.get(a);
                                    GoodInfo   goodInfo = new  GoodInfo();
                                    String item=goodsInfoNode.elementText("Item");//项目名称
                                    goodInfo.setItem(item);
                                    String specification=goodsInfoNode.elementText("Specification");//规格型号
                                    goodInfo.setSpecification(specification);
                                    String measurementDimension=goodsInfoNode.elementText("MeasurementDimension");//单位
                                    goodInfo.setMeasurementDimension(measurementDimension);
                                    String quantity=goodsInfoNode.elementText("Quantity");//数量
                                    if("0".equals(quantity)){
                                        goodInfo.setQuantity("");
                                    }else{
                                        goodInfo.setQuantity(quantity);
                                    }
                                    //goodInfo.setQuantity(quantity);
                                    String price=goodsInfoNode.elementText("Price");//单价
                                    if("0".equals(price)){
                                        goodInfo.setPrice("");
                                    }else{
                                        goodInfo.setPrice(price);
                                    }
                                    //goodInfo.setPrice(price);
                                    String amount=goodsInfoNode.elementText("Amount");//金额
                                    goodInfo.setAmount(amount);
                                    xuyeMoneyAmount += Double.parseDouble(amount);//续页总计
                                    String taxScheme=goodsInfoNode.elementText("TaxScheme");//税率
                                    goodInfo.setTaxScheme(taxScheme);
                                    String taxAmount=goodsInfoNode.elementText("TaxAmount");//税额
                                    if(taxAmount.indexOf("*")!=-1){

                                    }else{
                                        xuyetaxAmount += Double.parseDouble(taxAmount);  //续页税额总计
                                    }
                                    //xuyetaxAmount += Double.parseDouble(taxAmount);  //续页税额总计
                                    goodInfo.setTaxAmount(taxAmount);
                                    //String extention=goodsInfoNode.elementText("Extention");//序号
                                    goodInfo.setExtention_XH(Integer.toString(a+1));
                                    goodInfoList.add(goodInfo);
                                }
                                moneyAmount3 += xuyeMoneyAmount;
                                xuYePageContent.setSubtotalofAmount(baoliuliangwei(xuyeMoneyAmount));
                                taxAmount3  += xuyetaxAmount;
                                if(taxTotalAmount.indexOf("*")!=-1){
                                    xuYePageContent.setTaxAmountofSubtotal(taxTotalAmount);
                                }else{
                                    xuYePageContent.setTaxAmountofSubtotal(baoliuliangwei(xuyetaxAmount));
                                }
                                //xuYePageContent.setTaxAmountofSubtotal(baoliuliangwei(xuyetaxAmount));
                                xuYePageContent.setGoodInfoList(goodInfoList);
                                xuyepageContentList.add(xuYePageContent);
                                s +=26;
                            }
                        }
                    }else{
                        //尾页
                        weiyePageContent =  new  WeiYePageContent();
                        weiyePageContent.setBuyerName(buyerName);
                        weiyePageContent.setSellerName(sellerName);
                        weiyePageContent.setInvoiceCode(inVoiceCode);
                        weiyePageContent.setInvoiceNo(inVoiceNo);
                        //xuYePageContent.setSubtotalofAmount(subtotalofAmount);
                        weiyePageContent.setSubtotalofAmount(taxExclusiveTotalAmount);
                        weiyePageContent.setSubtotalofTotal(taxExclusiveTotalAmount);
                        weiyePageContent.setTaxAmountofSubtotal(taxTotalAmount);
                        weiyePageContent.setTaxAmountofTotal(taxTotalAmount);
                        weiyePageContent.setRemarks(note);
                        weiyePageContent.setDateofinvoice(issueDate);
                        weiyePageContent.setPageCount("1");
                        weiyePageContent.setPagePosition("1");
                        double   weiyeMoneyAmount = 0;
                        double   weiyetaxAmount = 0;
                        List<GoodInfo>   goodInfoList =  new ArrayList<GoodInfo>(goodsInfoNodes.size());
                        for(int i=0;i<goodsInfoNodes.size();i++){//获取某个清单
                            Element goodsInfoNode=goodsInfoNodes.get(i);
                            GoodInfo   goodInfo = new  GoodInfo();
                            String item=goodsInfoNode.elementText("Item");//项目名称
                            goodInfo.setItem(item);
                            String specification=goodsInfoNode.elementText("Specification");//规格型号
                            goodInfo.setSpecification(specification);
                            String measurementDimension=goodsInfoNode.elementText("MeasurementDimension");//单位
                            goodInfo.setMeasurementDimension(measurementDimension);
                            String quantity=goodsInfoNode.elementText("Quantity");//数量
                            if("0".equals(quantity)){
                                goodInfo.setQuantity("");
                            }else{
                                goodInfo.setQuantity(quantity);
                            }
                            //goodInfo.setQuantity(quantity);
                            String price=goodsInfoNode.elementText("Price");//单价
                            if("0".equals(price)){
                                goodInfo.setPrice("");
                            }else{
                                goodInfo.setPrice(price);
                            }
                            //goodInfo.setPrice(price);
                            String amount=goodsInfoNode.elementText("Amount");//金额
                            weiyeMoneyAmount += Double.parseDouble(amount);
                            goodInfo.setAmount(amount);
                            String taxScheme=goodsInfoNode.elementText("TaxScheme");//税率
                            goodInfo.setTaxScheme(taxScheme);
                            String taxAmount=goodsInfoNode.elementText("TaxAmount");//税额
                            if(taxAmount.indexOf("*")!=-1){

                            }else{
                                weiyetaxAmount += Double.parseDouble(taxAmount);
                            }
                            //weiyetaxAmount += Double.parseDouble(taxAmount);
                            goodInfo.setTaxAmount(taxAmount);
                            //String extention=goodsInfoNode.elementText("Extention");//序号
                            //goodInfo.setExtention_XH(extention);
                            goodInfo.setExtention_XH(Integer.toString(i+1));
                            goodInfoList.add(goodInfo);
                        }
                        if(taxExclusiveTotalAmount==null||"".equals(taxExclusiveTotalAmount)) {
                            shouYePageContent.setTaxExclusiveTotalAmount(baoliuliangwei(weiyeMoneyAmount));
                            weiyePageContent.setSubtotalofAmount(baoliuliangwei(weiyeMoneyAmount));
                            weiyePageContent.setSubtotalofTotal(baoliuliangwei(weiyeMoneyAmount));
                            shouyegoodInfo.setAmount(baoliuliangwei(weiyeMoneyAmount));
                        }
                        if(taxTotalAmount==null||"".equals(taxTotalAmount)) {
                            shouYePageContent.setTaxToTotalAmount(baoliuliangwei(weiyetaxAmount));
                            weiyePageContent.setTaxAmountofSubtotal(baoliuliangwei(weiyetaxAmount));
                            weiyePageContent.setTaxAmountofTotal(baoliuliangwei(weiyetaxAmount));
                            shouyegoodInfo.setTaxAmount(baoliuliangwei(weiyetaxAmount));
                        }else   if(taxTotalAmount.indexOf("*")!=-1){
                            shouYePageContent.setTaxToTotalAmount(baoliuliangwei(weiyetaxAmount));
                            weiyePageContent.setTaxAmountofSubtotal(taxTotalAmount);
                            weiyePageContent.setTaxAmountofTotal(baoliuliangwei(weiyetaxAmount));
                            shouyegoodInfo.setTaxAmount(taxTotalAmount);
                        }
                        if(taxInclusiveTotalAmount==null||"".equals(taxInclusiveTotalAmount)) {
                            shouYePageContent.setTaxInclusiveTotalAmount(baoliuliangwei(weiyeMoneyAmount+weiyetaxAmount));
                            shouYePageContent.setTaxInclusiveTotalAmountofCHS(NumberToCN.number2CNMontrayUnit(new BigDecimal(weiyeMoneyAmount+weiyetaxAmount)));
                        }else{
                            shouYePageContent.setTaxInclusiveTotalAmountofCHS(NumberToCN.number2CNMontrayUnit(new BigDecimal(taxInclusiveTotalAmount)));
                        }
                        shouyegoodInfo.setTaxScheme(goodInfoList.get(0).getTaxScheme());
                        weiyePageContent.setGoodInfoList(goodInfoList);
                    }
                    shouyeInfoList.add(shouyegoodInfo);
                    shouYePageContent.setGoodInfoList(shouyeInfoList);
                }
                dJFapiaoObj.setShouyePageContent(shouYePageContent);
                dJFapiaoObj.setXuyepageContentList(xuyepageContentList);
                dJFapiaoObj.setWeiyePageContent(weiyePageContent);

                /*创建临时文件夹及生成文件保存地址*/
                String upload_path = Util.getSystemDictionary("upload_path")+"/";
                String tempPath = Util.getSuffixFolder(upload_path+Util.getSystemDictionary("filePath")+"/");

                String path = Util.getSystemDictionary("templateSynthesis")+"/" +Util.getYYYYMMDDHH();
                String filePath = Util.getSuffixFolder(upload_path,path);


                String result = DjFapiaoUtil.makeFapiaoOFD(dJFapiaoObj, tempPath, filePath + fileName);
                log.info("result="+result);
                if(!"success".equals(result)){
                    return getElectronicReturnXml(null, fileName, result);
                }else{
                    retMap.put("RET_CODE", 1);
                    retMap.put("FILE_PATH", path);  //相对路径
                    retMap.put("FILE_PATH_ALL", filePath + fileName); //绝对路径

                    returnXml = getElectronicReturnXml(retMap, fileName);
                }
            }
        } catch (DocumentException e) {
            e.printStackTrace();
            returnXml = getElectronicReturnXml(null, fileName, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            returnXml = getElectronicReturnXml(null, fileName, e.getMessage());
        } finally {
            return returnXml;
        }
    }

    //处理double，四舍五入保留两位小数
    public  String baoliuliangwei(double XV){
        DecimalFormat df   = new DecimalFormat("######0.00");
        return df.format(XV);
    }
    //替换  xml内容
    private  String replaceXml(String   xml){
        return   xml.substring(xml.indexOf("<InvoiceCode>"),xml.indexOf("</EINVOICE>"));
    }
    /**
     * 得到返回报文(电子发票用)
     *
     * @param map
     * @param fileName   合成后的文件名
     * @param fileName   合成后的文件名
     * @param checkMsg 验证错误标记
     * @return 响应报文
     */
    public String getElectronicReturnXml( Map map, String fileName, String... checkMsg) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String requestUrl = request.getRequestURL().toString();
        String baseUrl = requestUrl.substring(0, requestUrl.indexOf(Util.getSystemDictionary("server.contextPath")));

        String retXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><EINVOICE_RESPONSE>";
        String msg = "";
        if (checkMsg.length == 0) {
            msg = "<RET_CODE>" + 1 + "</RET_CODE>"
                    + "<RET_MSG>xml验证成功</RET_MSG>";
            Iterator iterator = map.values().iterator();
            msg += "<FILE>";
            String fileData = "";
            String file_path_all = map.get("FILE_PATH_ALL").toString();
            if (map.containsKey("RET_FILE_TYPE") && "BASE64".equals(map.get("RET_FILE_TYPE"))) {
                log.info("file_path_all-----------------------------------" + file_path_all);
                fileData = "<FILE_DATA>" + Util.getFileBase642(file_path_all) + "</FILE_DATA>";
            } else {
                String path = map.get("FILE_PATH").toString();
                fileData = "<FILE_URL>" +  baseUrl + Util.getSystemDictionary("server.contextPath") + "/file/" +path +"?name=" + fileName + "</FILE_URL>";
            }

            msg += "<RET_CODE>1</RET_CODE>"
                        + "<FILE_NAME>" + fileName + "</FILE_NAME>"
                        + "<FILE_MSG>" + "电子发票生成成功" + "</FILE_MSG>"
                        + fileData + "</FILE>";
        } else {
            msg += "<FILE><RET_CODE>0</RET_CODE>"
                    + "<FILE_NAME>" + fileName + "</FILE_NAME>"
                    + "<FILE_MSG>" + "电子发票生成失败" + "</FILE_MSG>";
            msg += "<RET_MSG>" + checkMsg[0] + "</RET_MSG></FILE>";
        }
        retXml += msg + "</EINVOICE_RESPONSE>";
        return retXml;
    }
}
