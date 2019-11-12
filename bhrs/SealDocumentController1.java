//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.dianju.modules.document.controllers;

import com.alibaba.fastjson.JSONObject;
import com.dianju.core.Response;
import com.dianju.core.Util;
import com.dianju.core.EncryptionAndDecryption.DES.DesUtil;
import com.dianju.core.EncryptionAndDecryption.ECC.ECCUtil;
import com.dianju.core.models.UUIDReduce;
import com.dianju.core.models.pageAndSizeException;
import com.dianju.modules.document.models.SealDocument;
import com.dianju.modules.document.models.SealDocumentDao;
import com.dianju.modules.document.models.SealLog;
import com.dianju.modules.document.models.SealLogDao;
import com.dianju.modules.org.models.DepartmentDao;
import com.dianju.modules.org.models.user.User;
import com.dianju.modules.org.models.user.UserDao;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

@RestController
@RequestMapping({"/api"})
public class SealDocumentController1 {
    @Autowired
    SealDocumentDao sealDocumentDao;
    @Autowired
    DepartmentDao departmentDao;
    @Autowired
    SealLogDao sealLogDao;
    @Autowired
    UserDao userDao;

    public SealDocumentController() {
    }

    @RequestMapping(
            path = {"/getSealDocuments"},
            method = {RequestMethod.GET},
            produces = {"application/json"}
    )
    public ResponseEntity getSealDocuments(@RequestParam Map map) throws pageAndSizeException {
        Page page = null;
        page = this.sealDocumentDao.getSealDocuments(map);
        return new ResponseEntity(page, HttpStatus.OK);
    }

    @RequestMapping(
            path = {"/getSealLogs"},
            method = {RequestMethod.GET},
            produces = {"application/json"}
    )
    public ResponseEntity getSealLogs(@RequestParam Map map) throws pageAndSizeException {
        Page page = null;
        page = this.sealLogDao.getSealLogs(map);
        return new ResponseEntity(page, HttpStatus.OK);
    }

    @RequestMapping(
            path = {"/getSealDocument/{id}"},
            method = {RequestMethod.GET},
            produces = {"application/json"}
    )
    public ResponseEntity getSealDocument(@PathVariable String id) {
        SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
        return new ResponseEntity(sealDocument, HttpStatus.OK);
    }

    @RequestMapping(
            path = {"/sealDownLoad"},
            method = {RequestMethod.GET},
            produces = {"application/json"}
    )
    public void sealDownLoad(@RequestParam String id, HttpServletResponse response) throws Exception {
        SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
        InputStream fin = null;
        ServletOutputStream out = null;

        try {
            File file = new File(Util.getSystemDictionary("sealedBasePath") + sealDocument.getSealedPath());
            fin = new FileInputStream(file);
            out = response.getOutputStream();
            response.setCharacterEncoding("utf-8");
            response.setContentType("application/x-download");
            String fileName = sealDocument.getDocumentName() + ".pdf";
            response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            byte[] buffer = new byte[1024];
            boolean var9 = true;

            int bytesToRead;
            while((bytesToRead = fin.read(buffer)) != -1) {
                out.write(buffer, 0, bytesToRead);
            }
        } catch (Exception var13) {
            var13.printStackTrace();
        } finally {
            if (fin != null) {
                fin.close();
            }

            if (out != null) {
                out.close();
            }

        }

    }

    @RequestMapping(
            path = {"/sealLogExcel"},
            method = {RequestMethod.GET},
            produces = {"application/json"}
    )
    public void sealLogExcel(@RequestParam String sealManager, @RequestParam String roleName, @RequestParam String sealedStartTime, @RequestParam String sealedEndTime, HttpServletResponse response) throws Exception {
        System.out.println(roleName);
        String[] title = new String[]{"用印日期", "公文编号", "文件类型", "文件名称", "批准人", "批准日期", "经办单位", "经办人", "印章名称", "盖印数", "盖印人"};
        String sheetName = "部门用印表";
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String fileName = "盖章记录表-" + df.format(System.currentTimeMillis()) + ".xls";
        Map map = new HashMap();
        if (roleName.equals("contractUser")) {
            map.put("sealedStartTime", df.parse(sealedStartTime.replace("\"", "").substring(0, 10)).getTime() / 1000L - 86400L);
            map.put("sealedEndTime", df.parse(sealedEndTime.replace("\"", "").substring(0, 10)).getTime() / 1000L + 172800L);
        } else {
            map.put("sealManager", sealManager);
            map.put("sealedStartTime", df.parse(sealedStartTime.replace("\"", "").substring(0, 10)).getTime() / 1000L - 86400L);
            map.put("sealedEndTime", df.parse(sealedEndTime.replace("\"", "").substring(0, 10)).getTime() / 1000L + 172800L);
        }

        List<Map<String, Object>> list = this.sealLogDao.getSealLogsExcel(map);
        String[][] content = new String[list.size()][];

        for(int i = 0; i < list.size(); ++i) {
            content[i] = new String[title.length];
            content[i][0] = df.format(Long.valueOf(((Map)list.get(i)).get("sealedAt").toString()) * 1000L);
            content[i][1] = ((Map)list.get(i)).get("reportId").toString();
            content[i][2] = ((Map)list.get(i)).get("documentType").toString();
            content[i][3] = ((Map)list.get(i)).get("documentName").toString();
            content[i][4] = ((Map)list.get(i)).get("approvePerson").toString();
            content[i][5] = df.format(Long.valueOf(((Map)list.get(i)).get("reportDate").toString()) * 1000L);
            content[i][6] = ((Map)list.get(i)).get("deptAllName").toString();
            content[i][7] = ((Map)list.get(i)).get("reportPerson").toString();
            content[i][8] = ((Map)list.get(i)).get("sealName").toString();
            content[i][9] = ((Map)list.get(i)).get("sealCount").toString();
            content[i][10] = ((Map)list.get(i)).get("sealManager").toString();
        }

        HSSFWorkbook wb = this.getHSSFWorkbook(sheetName, title, content, (HSSFWorkbook)null);

        try {
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            response.addHeader("Pargam", "no-cache");
            response.addHeader("Cache-Control", "no-cache");
            OutputStream os = response.getOutputStream();
            wb.write(os);
            os.flush();
            os.close();
        } catch (Exception var15) {
            var15.printStackTrace();
        }

    }

    @RequestMapping(
            path = {"/deleteSealDocument"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity deleteSealDocument(@RequestBody String id) throws Exception {
        SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
        sealDocument.setStatus((byte)11);
        this.sealDocumentDao.save(sealDocument);
        return new ResponseEntity(new Response(200, "OK"), HttpStatus.OK);
    }

    @RequestMapping(
            path = {"/updatePrintNumber"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity updatePrintNumber(@RequestBody Map map) throws Exception {
        String id = map.get("id").toString();
        int printNum = Integer.valueOf(map.get("printNum").toString());
        SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
        sealDocument.setPrintTotalNum(sealDocument.getPrintTotalNum() + printNum);
        this.sealDocumentDao.save(sealDocument);
        return new ResponseEntity(new Response(200, "OK"), HttpStatus.OK);
    }

    @RequestMapping(
            path = {"/getNormalFileBase64"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity getNormalFileBase64(@RequestBody Map map) throws Exception {
        Map map1 = new HashMap();
        String id = map.get("id").toString();
        String sign = map.get("sign").toString();
        if (id != null && !id.equals("")) {
            SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
            int restPrintCount = sealDocument.getPrintTotalNum() - sealDocument.getPrintedNum();
            map1.put("saveFlag", sealDocument.getPrintOrDownload());
            map1.put("restPrintCount", restPrintCount);
            String fileBase64 = "";
            if (sign.equals("1")) {
                fileBase64 = this.fileToBase64(Util.getSystemDictionary("sealBasePath"), sealDocument.getDocumentPath());
                map1.put("fileBase64", fileBase64);
                return new ResponseEntity(map1, HttpStatus.OK);
            } else if (sign.equals("2")) {
                fileBase64 = this.fileToBase64(Util.getSystemDictionary("sealedBasePath"), sealDocument.getSealedPath());
                map1.put("fileBase64", fileBase64);
                return new ResponseEntity(map1, HttpStatus.OK);
            } else {
                return new ResponseEntity(new Response(200, "OK"), HttpStatus.OK);
            }
        } else {
            return new ResponseEntity(new Response(3100, "查找文件失败"), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(
            path = {"/getDesFileBase64"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity getFileBase64(@RequestBody Map map) throws Exception {
        Map map1 = new HashMap();
        String key = Util.getSystemDictionary("fileKey");
        String sealId = map.get("id").toString();
        DesUtil des = new DesUtil(key);
        String deCryptId = des.decrypt(sealId);
        map1.put("documentId", deCryptId);
        String sign = map.get("sign").toString();
        if (sealId != null && !sealId.equals("")) {
            SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(deCryptId);
            map1.put("saveFlag", sealDocument.getPrintOrDownload());
            String fileBase64 = "";
            if (sign.equals("1")) {
                fileBase64 = this.fileToBase64(Util.getSystemDictionary("sealBasePath"), sealDocument.getDocumentPath());
                map1.put("fileBase64", fileBase64);
            } else if (sign.equals("2")) {
                fileBase64 = this.fileToBase64(Util.getSystemDictionary("sealedBasePath"), sealDocument.getSealedPath());
            }

            map1.put("fileBase64", fileBase64);
            return new ResponseEntity(map1, HttpStatus.OK);
        } else {
            return new ResponseEntity(new Response(3100, "查找文件失败"), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(
            path = {"/saveFile"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity saveFile(@RequestBody Map map) throws Exception {
        Long nowTime = System.currentTimeMillis();
        if (Util.isBlank(map.get("id").toString())) {
            return new ResponseEntity(new Response(3100, "查找文件失败"), HttpStatus.BAD_REQUEST);
        } else if (Util.isBlank(map.get("pdFlag").toString())) {
            return new ResponseEntity(new Response(3100, "选择失败"), HttpStatus.BAD_REQUEST);
        } else {
            SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(map.get("id").toString());
            String tempDocumentName = sealDocument.getDocumentPath().substring(sealDocument.getDocumentPath().lastIndexOf("/") + 1, sealDocument.getDocumentPath().length());
            this.base64ToFile(this.mkdir(Util.getSystemDictionary("sealedBasePath")) + "/" + tempDocumentName, map.get("fileBase64").toString());
            sealDocument.setPrintOrDownload(Integer.parseInt(map.get("pdFlag").toString()));
            if (map.get("pdFlag").toString().equals("1") || map.get("pdFlag").toString().equals("3")) {
                sealDocument.setPrintTotalNum(Integer.parseInt(map.get("printNumber").toString()));
            }

            sealDocument.setSealedAt(nowTime / 1000L);
            sealDocument.setSealStatus(2);
            sealDocument.setUploadMode(1);
            sealDocument.setSealedPath(this.getMidPath() + "/" + tempDocumentName);
            this.saveSeal(map.get("sealJsonStr").toString(), map.get("documentId").toString(), map.get("sealManager").toString());
            this.sealDocumentDao.save(sealDocument);
            String key = Util.getSystemDictionary("fileKey");
            DesUtil des = new DesUtil(key);
            String enCryptId = des.encrypt(sealDocument.getId());
            String redirectUrl = Util.getSystemDictionary("jumpUrl") + "/ESS/#/getInfo?fileId=" + enCryptId;
            System.out.println(redirectUrl);
            String result = this.addSendInfo(redirectUrl, sealDocument);
            if (result.equals("1")) {
                sealDocument.setHandleResult(Integer.parseInt("1"));
                sealDocument.setReUpdateTime(nowTime / 1000L);
                this.sealDocumentDao.save(sealDocument);
                return new ResponseEntity(new Response(200, "保存成功"), HttpStatus.OK);
            } else {
                sealDocument.setHandleResult(Integer.parseInt("0"));
                sealDocument.setReUpdateTime(nowTime / 1000L);
                this.sealDocumentDao.save(sealDocument);
                return new ResponseEntity(new Response(500, "发送文件失败,请重新发送"), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @RequestMapping(
            path = {"/saveUploadFile"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity saveUploadFile(@RequestBody Map map) throws Exception {
        Date date = null;
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Long nowTime = System.currentTimeMillis();
        String tempDocumentName = UUID.randomUUID().toString();
        String sealDocumentName = map.get("realDocumentName").toString();
        String sealDocumentType = "pdf";
        SealDocument sealDocument = new SealDocument();
        sealDocument.setDocumentId(tempDocumentName);
        if (!map.get("id").toString().equals("") && !map.get("id").toString().equals((Object)null)) {
            sealDocument.setId(map.get("id").toString());
        } else {
            sealDocument.setId(UUIDReduce.uuid());
        }

        sealDocument.setReportTitle(map.get("reportTitle").toString());
        sealDocument.setReportPerson(map.get("reportPerson").toString());
        sealDocument.setApprovePerson(map.get("approvePerson").toString());
        sealDocument.setReceivePerson(map.get("receivePerson").toString());
        sealDocument.setDocumentName(sealDocumentName);
        date = format.parse(String.valueOf(map.get("reportDate")));
        Long time = date.getTime();
        sealDocument.setReportDate(time / 1000L);
        sealDocument.setSealedAt(nowTime / 1000L);
        sealDocument.setReportId(map.get("reportId").toString());
        sealDocument.setDeptAllName(map.get("reportDeptAllName").toString());
        sealDocument.setSealManager(map.get("sealManager").toString());
        sealDocument.setDocumentType(sealDocumentType);
        sealDocument.setPrintOrDownload(Integer.parseInt(map.get("pdFlag").toString()));
        if (map.get("pdFlag").toString().equals("1") || map.get("pdFlag").toString().equals("3")) {
            sealDocument.setPrintTotalNum(Integer.parseInt(map.get("printNumber").toString()));
        }

        this.base64ToFile(this.mkdir(Util.getSystemDictionary("sealedBasePath")) + "/" + tempDocumentName + "." + sealDocumentType, map.get("fileBase64").toString());
        sealDocument.setSealedPath(this.getMidPath() + "/" + tempDocumentName + "." + sealDocumentType);
        sealDocument.setSealStatus(2);
        sealDocument.setUploadMode(2);
        sealDocument.setSealedAt(nowTime / 1000L);
        if (map.get("remark").toString().equals("") || map.get("remark").toString().equals((Object)null)) {
            sealDocument.setRemark("");
        }

        this.saveSeal(map.get("sealJsonStr").toString(), tempDocumentName, map.get("sealManager").toString());
        this.sealDocumentDao.save(sealDocument);
        String key = Util.getSystemDictionary("fileKey");
        DesUtil des = new DesUtil(key);
        String enCryptId = des.encrypt(sealDocument.getId());
        String redirectUrl = Util.getSystemDictionary("jumpUrl") + "/ESS/#/getInfo?fileId=" + enCryptId;
        String result = this.addInfo(redirectUrl, sealDocument);
        if (result.equals("1")) {
            sealDocument.setHandleResult(Integer.parseInt("1"));
            sealDocument.setReUpdateTime(nowTime / 1000L);
            this.sealDocumentDao.save(sealDocument);
            return new ResponseEntity(new Response(200, "保存成功"), HttpStatus.OK);
        } else {
            sealDocument.setHandleResult(Integer.parseInt("0"));
            sealDocument.setReUpdateTime(nowTime / 1000L);
            this.sealDocumentDao.save(sealDocument);
            return new ResponseEntity(new Response(500, "发送文件失败,请重新发送"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(
            path = {"/getUserNames"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity getUserNames(@RequestBody String name) throws Exception {
        List<User> list = this.userDao.findUserByName(name);
        List<String> list1 = new ArrayList();

        for(int i = 0; i < list.size(); ++i) {
            list1.add(((User)list.get(i)).getName());
        }

        return list1.size() != 0 && list1 != null ? new ResponseEntity(list1, HttpStatus.OK) : new ResponseEntity(new Response(500, "无此人信息"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RequestMapping(
            path = {"/reSendFile/{id}"},
            method = {RequestMethod.GET},
            produces = {"application/json"}
    )
    public ResponseEntity reSendFile(@PathVariable String id) throws Exception {
        Long nowTime = null;
        SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
        String key = Util.getSystemDictionary("fileKey");
        DesUtil des = new DesUtil(key);
        String enCryptId = des.encrypt(sealDocument.getId());
        String redirectUrl = Util.getSystemDictionary("jumpUrl") + "/ESS/#/getInfo?fileId=" + enCryptId;
        String result = this.addInfo(redirectUrl, sealDocument);
        if (result.equals("1")) {
            nowTime = System.currentTimeMillis();
            sealDocument.setHandleResult(Integer.parseInt("1"));
            sealDocument.setReUpdateTime(nowTime / 1000L);
            this.sealDocumentDao.save(sealDocument);
            return new ResponseEntity(new Response(200, "保存成功"), HttpStatus.OK);
        } else {
            nowTime = System.currentTimeMillis();
            sealDocument.setHandleResult(Integer.parseInt("0"));
            sealDocument.setReUpdateTime(nowTime / 1000L);
            this.sealDocumentDao.save(sealDocument);
            return new ResponseEntity(new Response(500, "保存失败"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @RequestMapping(
            path = {"/verifyLogin"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity verifyLogin(@RequestBody Map map) throws Exception {
        String userId = map.get("userId").toString();
        String currentTime = map.get("currentTime").toString();
        String desKey = Util.getSystemDictionary("loginKey");
        DesUtil des = new DesUtil(desKey);
        String deCryptUserId = des.decrypt(userId);
        DesUtil des1 = new DesUtil(desKey);
        String deCryptCurrentTime = des1.decrypt(currentTime);
        Long nowTime = System.currentTimeMillis();
        User user = this.userDao.findUserByLoginId(deCryptUserId);
        if (user == null) {
            return new ResponseEntity(new Response(500, "用户不存在"), HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            return nowTime - Long.valueOf(deCryptCurrentTime) > 86400000L ? new ResponseEntity(new Response(500, "登录超时"), HttpStatus.INTERNAL_SERVER_ERROR) : new ResponseEntity(user, HttpStatus.OK);
        }
    }

    @RequestMapping(
            path = {"/updatePrint"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity updatePrint(@RequestBody Map map) throws Exception {
        String id = map.get("id").toString();
        String printNumber = map.get("printNumber").toString();
        SealDocument sealDocument = this.sealDocumentDao.findSealDocuemntById(id);
        sealDocument.setPrintedNum(Integer.valueOf(printNumber) + sealDocument.getPrintedNum());
        this.sealDocumentDao.save(sealDocument);
        return new ResponseEntity(new Response(200, "打印成功"), HttpStatus.OK);
    }

    @RequestMapping(
            path = {"/getQRcode"},
            method = {RequestMethod.POST},
            produces = {"application/json"}
    )
    public ResponseEntity getQRcode(@RequestBody Map map) throws Exception {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Map map1 = new HashMap();
        String QRcode = "";
        String id = map.get("id").toString();
        String reportId;
        String sealDocumentName;
        String documentName;
        String finalName;
        if (id.equals("")) {
            reportId = map.get("reportId").toString();
            sealDocumentName = map.get("realDocumentName").toString();
            documentName = sealDocumentName.substring(sealDocumentName.lastIndexOf("\\") + 1, sealDocumentName.length());
            finalName = documentName.substring(0, documentName.lastIndexOf("."));
            String reportDate = df.format(new Date());
            String uid = UUIDReduce.uuid();
            String oriData = uid + ";" + reportId + ";" + finalName + ";" + reportDate;
            QRcode = ECCUtil.encryptByPublicKey(oriData, Util.getSystemDictionary("publicKey"));
            map1.put("id", uid);
            map1.put("QRcode", QRcode);
        } else {
            reportId = map.get("reportId").toString();
            sealDocumentName = map.get("documentName").toString();
            documentName = df.format(new Date());
            finalName = id + ";" + reportId + ";" + sealDocumentName + ";" + documentName;
            QRcode = ECCUtil.encryptByPublicKey(finalName, Util.getSystemDictionary("publicKey"));
            map1.put("QRcode", QRcode);
        }

        return new ResponseEntity(map1, HttpStatus.OK);
    }

    public String fileToBase64(String basePath, String filePath) throws IOException {
        File file = new File(basePath + filePath);
        FileInputStream inputFile = new FileInputStream(file);
        byte[] buffer = new byte[(int)file.length()];
        inputFile.read(buffer);
        inputFile.close();
        return (new BASE64Encoder()).encode(buffer);
    }

    public void base64ToFile(String finalPath, String fileBase64) {
        try {
            byte[] buffer = (new BASE64Decoder()).decodeBuffer(fileBase64);
            FileOutputStream out = new FileOutputStream(finalPath);
            out.write(buffer);
            out.close();
        } catch (Exception var5) {
            throw new RuntimeException("base64字符串异常或地址异常\n" + var5.getMessage());
        }
    }

    public String mkdir(String basePath) {
        Calendar c = Calendar.getInstance();
        int year = c.get(1);
        int month = c.get(2) + 1;
        int day = c.get(5);
        String yearStr = Integer.toString(year);
        String monthStr = Integer.toString(month);
        String dayStr = Integer.toString(day);
        String filePath = yearStr + "/" + monthStr + "/" + dayStr;
        String muluStr = basePath + filePath;
        File filemulu = new File(muluStr);
        if (!filemulu.exists() && !filemulu.isDirectory()) {
            filemulu.mkdirs();
        }

        return muluStr;
    }

    public String getMidPath() {
        Calendar c = Calendar.getInstance();
        int year = c.get(1);
        int month = c.get(2) + 1;
        int day = c.get(5);
        String yearStr = Integer.toString(year);
        String monthStr = Integer.toString(month);
        String dayStr = Integer.toString(day);
        String filePath = yearStr + "/" + monthStr + "/" + dayStr;
        return filePath;
    }

    public String addInfo(String redirectUrl, SealDocument sealDocument) {
        String url = Util.getSystemDictionary("asmxUrl");
        String soapaction = Util.getSystemDictionary("soapaction");
        Service service = new Service();
        String message = "1";

        try {
            Call call = (Call)service.createCall();
            call.setTargetEndpointAddress(url);
            call.setOperationName(new QName(soapaction, "AddInfo"));
            call.addParameter(new QName(soapaction, "XMLInfo"), XMLType.XSD_STRING, ParameterMode.IN);
            call.setReturnType(new QName(soapaction, "AddInfo"), String.class);
            call.setReturnType(XMLType.XSD_STRING);
            call.setUseSOAPAction(true);
            call.setSOAPActionURI(soapaction + "AddInfo");
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            long lt = sealDocument.getUpdatedAt() * 1000L;
            Date date = new Date(lt);
            User user = this.userDao.findOneUserByName(sealDocument.getReceivePerson());
            if (user == null || user.equals("")) {
                message = "用户不存在";
            }

            String i = " <ZDATA><ITEM><PT_USERID>" + user.getLoginId() + "</PT_USERID><TITLE>" + sealDocument.getReportTitle() + "</TITLE><ANAME>" + sealDocument.getDocumentName() + "</ANAME><FTIME>" + simpleDateFormat.format(date) + "</FTIME><TYPE>" + sealDocument.getDocumentType() + "</TYPE><FURL>" + redirectUrl + "</FURL></ITEM></ZDATA>";
            String xmlStr = (String)call.invoke(new Object[]{i});
            System.out.println("发送的数据串-------------------" + i);
            Document document = DocumentHelper.parseText(xmlStr);
            Element root = document.getRootElement();
            Iterator<Element> e1 = root.elementIterator();
            Element e2 = (Element)e1.next();
            message = e2.getText().trim();
        } catch (Exception var19) {
            var19.printStackTrace();
        }

        return message;
    }

    public String addSendInfo(String redirectUrl, SealDocument sealDocument) {
        String url = Util.getSystemDictionary("asmxUrl");
        String soapaction = Util.getSystemDictionary("soapaction");
        Service service = new Service();
        String message = "1";

        try {
            Call call = (Call)service.createCall();
            call.setTargetEndpointAddress(url);
            call.setOperationName(new QName(soapaction, "AddInfo"));
            call.addParameter(new QName(soapaction, "XMLInfo"), XMLType.XSD_STRING, ParameterMode.IN);
            call.setReturnType(new QName(soapaction, "AddInfo"), String.class);
            call.setReturnType(XMLType.XSD_STRING);
            call.setUseSOAPAction(true);
            call.setSOAPActionURI(soapaction + "AddInfo");
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            long lt = sealDocument.getUpdatedAt() * 1000L;
            Date date = new Date(lt);
            String i = " <ZDATA><ITEM><PT_USERID>" + sealDocument.getReceivePerson() + "</PT_USERID><TITLE>" + sealDocument.getReportTitle() + "</TITLE><ANAME>" + sealDocument.getDocumentName() + "</ANAME><FTIME>" + simpleDateFormat.format(date) + "</FTIME><TYPE>" + sealDocument.getDocumentType() + "</TYPE><FURL>" + redirectUrl + "</FURL></ITEM></ZDATA>";
            String xmlStr = (String)call.invoke(new Object[]{i});
            System.out.println("发送的数据串-------------------" + i);
            Document document = DocumentHelper.parseText(xmlStr);
            Element root = document.getRootElement();
            Iterator<Element> e1 = root.elementIterator();
            Element e2 = (Element)e1.next();
            message = e2.getText().trim();
        } catch (Exception var18) {
            var18.printStackTrace();
        }

        return message;
    }

    public void saveSeal(String str, String documentId, String sealManager) {
        List<SealLog> list = JSONObject.parseArray(str, SealLog.class);
        Map<String, Integer> map = new HashMap();
        Map<String, String> map1 = new HashMap();

        for(int i = 0; i < list.size(); ++i) {
            String sealId = ((SealLog)list.get(i)).getSealId();
            String sealName = ((SealLog)list.get(i)).getSealName();
            if (map.containsKey(sealId)) {
                map.put(sealId, (Integer)map.get(sealId) + 1);
            } else {
                map.put(sealId, 1);
            }

            map1.put(sealId, sealName);
        }

        Iterator var10 = map.entrySet().iterator();

        while(var10.hasNext()) {
            Entry<String, Integer> entry = (Entry)var10.next();
            SealLog sealLog = new SealLog();
            sealLog.setSealId((String)entry.getKey());
            sealLog.setSealCount((Integer)entry.getValue());
            sealLog.setSealName((String)map1.get(entry.getKey()));
            sealLog.setDocuemntId(documentId);
            sealLog.setSealManager(sealManager);
            this.sealLogDao.save(sealLog);
        }

    }

    public HSSFWorkbook getHSSFWorkbook(String sheetName, String[] title, String[][] values, HSSFWorkbook wb) {
        if (wb == null) {
            wb = new HSSFWorkbook();
        }

        HSSFSheet sheet = wb.createSheet(sheetName);
        sheet.setDefaultColumnWidth(20);
        HSSFRow row = sheet.createRow(0);
        HSSFCellStyle cellStyle = wb.createCellStyle();
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        HSSFCell cell = null;

        int i;
        for(i = 0; i < title.length; ++i) {
            cell = row.createCell(i);
            cell.setCellValue(title[i]);
            cell.setCellStyle(cellStyle);
        }

        for(i = 0; i < values.length; ++i) {
            row = sheet.createRow(i + 1);

            for(int j = 0; j < values[i].length; ++j) {
                row.createCell(j).setCellValue(values[i][j]);
            }
        }

        return wb;
    }
}
