package com.dianju.modules.document.controllers;

import com.alibaba.fastjson.JSONObject;
import com.dianju.core.EncryptionAndDecryption.DES.DesUtil;
import com.dianju.core.EncryptionAndDecryption.ECC.ECCUtil;
import com.dianju.core.ErrorCode;
import com.dianju.core.Response;
import com.dianju.core.Util;
import com.dianju.core.models.UUIDReduce;
import com.dianju.core.models.pageAndSizeException;
import com.dianju.modules.document.models.SealDocument;
import com.dianju.modules.document.models.SealDocumentDao;
import com.dianju.modules.document.models.SealLog;
import com.dianju.modules.document.models.SealLogDao;
import com.dianju.modules.org.models.Department;
import com.dianju.modules.org.models.DepartmentDao;
import com.dianju.modules.org.models.user.User;
import com.dianju.modules.org.models.user.UserDao;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import java.io.*;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;


@RestController
@RequestMapping("/api")
public class SealDocumentController {

  @RequestMapping(path ="/getSealDocuments",method = RequestMethod.GET, produces = "application/json")
  public ResponseEntity getSealDocuments(@RequestParam Map map) throws pageAndSizeException {
    Page page = null;
    page = sealDocumentDao.getSealDocuments(map);
    return new ResponseEntity<>(page, HttpStatus.OK);
  }

  @RequestMapping(path ="/getSealLogs",method = RequestMethod.GET, produces = "application/json")
  public ResponseEntity getSealLogs(@RequestParam Map map) throws pageAndSizeException {
    Page page = null;
    page = sealLogDao.getSealLogs(map);
    return new ResponseEntity<>(page, HttpStatus.OK);
  }

  @RequestMapping(path ="/getSealDocument/{id}",method = RequestMethod.GET, produces = "application/json")
  public ResponseEntity getSealDocument(@PathVariable String id){
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(id);
    return new ResponseEntity<>(sealDocument, HttpStatus.OK);
  }

  @RequestMapping(path ="/getSealDocumentByReportId/{reportId}",method = RequestMethod.GET, produces = "application/json")
  public ResponseEntity getSealDocumentByReportId(@PathVariable String reportId){
    List<SealDocument> sealDocumentList = sealDocumentDao.findSealDocuemntByReportId(reportId);
    if(sealDocumentList!=null&&sealDocumentList.size()!=0){
      return new ResponseEntity<>(sealDocumentList.get(0), HttpStatus.OK);
    }
    return new ResponseEntity<>(null, HttpStatus.OK);
  }
  @RequestMapping(path = "/sealDownLoad", method = RequestMethod.GET ,produces = "application/json")
  public void sealDownLoad(@RequestParam String id, HttpServletResponse response) throws Exception {
    SealDocument sealDocument  = sealDocumentDao.findSealDocuemntById(id);
    InputStream fin = null;
    ServletOutputStream out = null;
    try {
      File file = new File(Util.getSystemDictionary("sealedBasePath")+sealDocument.getSealedPath());
      fin = new FileInputStream(file);
      out = response.getOutputStream();
      response.setCharacterEncoding("utf-8");
      response.setContentType("application/x-download");
      String fileName = sealDocument.getDocumentName()+".pdf";
      response.addHeader("Content-Disposition", "attachment;filename="+URLEncoder.encode(fileName, "UTF-8"));
      byte[] buffer = new byte[1024];
      int bytesToRead = -1;
      // 通过循环将读入的Word文件的内容输出到浏览器中
      while((bytesToRead = fin.read(buffer)) != -1) {
        out.write(buffer, 0, bytesToRead);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if(fin != null) fin.close();
      if(out != null) out.close();

    }
  }

  @RequestMapping(path = "/sealLogExcel", method = RequestMethod.GET ,produces = "application/json")
  public void sealLogExcel(@RequestParam String sealManager,@RequestParam String roleName, @RequestParam String sealedStartTime, @RequestParam String sealedEndTime,HttpServletResponse response) throws Exception {
    System.out.println(roleName);
    String[] title = {"用印日期","公文编号","文件类型","文件名称","批准人","批准日期","经办单位","经办人","印章名称","盖印数","盖印人"};
    String sheetName = "部门用印表";
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    String fileName = "盖章记录表-"+df.format(System.currentTimeMillis())+".xls";
    Map map = new HashMap();
    if(roleName.equals("contractUser")){
      map.put("sealedStartTime",df.parse(sealedStartTime.replace("\"", "").substring(0,10)).getTime()/1000-86400);
      map.put("sealedEndTime",df.parse(sealedEndTime.replace("\"", "").substring(0,10)).getTime()/1000+172800);
    }else {
      map.put("sealManager",sealManager);
      map.put("sealedStartTime",df.parse(sealedStartTime.replace("\"", "").substring(0,10)).getTime()/1000-86400);
      map.put("sealedEndTime",df.parse(sealedEndTime.replace("\"", "").substring(0,10)).getTime()/1000+172800);
    }
    List<Map<String, Object>> list = sealLogDao.getSealLogsExcel(map);
    String [][] content = new String[list.size()][];
    for(int i=0; i<list.size();i++){
      content[i] = new String[title.length];
      content[i][0] = df.format(Long.valueOf(list.get(i).get("sealedAt").toString())*1000);
      content[i][1] = list.get(i).get("reportId").toString();
      content[i][2] = list.get(i).get("documentType").toString();
      content[i][3] = list.get(i).get("documentName").toString();
      content[i][4] = list.get(i).get("approvePerson").toString();
      content[i][5] = df.format(Long.valueOf(list.get(i).get("reportDate").toString())*1000);
      content[i][6] = list.get(i).get("deptAllName").toString();
      content[i][7] = list.get(i).get("reportPerson").toString();
      content[i][8] = list.get(i).get("sealName").toString();
      content[i][9] = list.get(i).get("sealCount").toString();
      content[i][10] = list.get(i).get("sealManager").toString();
    }
    HSSFWorkbook wb = getHSSFWorkbook(sheetName, title, content, null);
    try {
      response.setContentType("application/octet-stream;charset=UTF-8");
      response.setHeader("Content-Disposition", "attachment;filename="+URLEncoder.encode(fileName, "UTF-8"));
      response.addHeader("Pargam", "no-cache");
      response.addHeader("Cache-Control", "no-cache");
      OutputStream os = response.getOutputStream();
      wb.write(os);
      os.flush();
      os.close();
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  @RequestMapping(path ="/deleteSealDocument",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity deleteSealDocument(@RequestBody String id) throws Exception {
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(id);
    sealDocument.setStatus((byte)11);
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(200,"OK"), HttpStatus.OK);
  }


  @RequestMapping(path ="/updatePrintNumber",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity updatePrintNumber(@RequestBody Map map) throws Exception {
    String id = map.get("id").toString();
    int printNum = Integer.valueOf(map.get("printNum").toString());
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(id);
    sealDocument.setPrintTotalNum(sealDocument.getPrintTotalNum() + printNum);
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(200,"OK"), HttpStatus.OK);
  }

  @RequestMapping(path ="/getNormalFileBase64",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity getNormalFileBase64(@RequestBody Map map) throws Exception {
    Map map1 = new HashMap();
    String id = map.get("id").toString();
    String sign = map.get("sign").toString();
    if (id == null||id.equals("")){
      return new ResponseEntity<>(new Response(ErrorCode.ERR_DATA_INVALID,"查找文件失败"), HttpStatus.BAD_REQUEST);
    }
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(id);
    int restPrintCount = sealDocument.getPrintTotalNum()-sealDocument.getPrintedNum();
    map1.put("saveFlag",sealDocument.getPrintOrDownload());
    map1.put("restPrintCount",restPrintCount);
    String fileBase64 = "";
    if(sign.equals("1")){
      fileBase64 = fileToBase64(Util.getSystemDictionary("sealBasePath"),sealDocument.getDocumentPath());
//			fileBase64 = fileToBase64("",sealDocument.getDocumentPath());
      map1.put("fileBase64", fileBase64);
      return new ResponseEntity<>(map1, HttpStatus.OK);
    }else if(sign.equals("2")) {
      fileBase64 = fileToBase64(Util.getSystemDictionary("sealedBasePath"), sealDocument.getSealedPath());
//            fileBase64 = fileToBase64("", sealDocument.getSealedPath());
      map1.put("fileBase64", fileBase64);
      return new ResponseEntity<>(map1, HttpStatus.OK);
    }
    return new ResponseEntity<>(new Response(200,"OK"), HttpStatus.OK);
  }

  @RequestMapping(path ="/getDesFileBase64",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity getFileBase64(@RequestBody Map map) throws Exception {
    Map map1 = new HashMap();
    String key = Util.getSystemDictionary("fileKey");
    String sealId = map.get("id").toString();
    DesUtil des = new DesUtil(key);
    String deCryptId = des.decrypt(sealId);

    map1.put("documentId", deCryptId);
    String sign = map.get("sign").toString();
    if (sealId == null||sealId.equals("")){
      return new ResponseEntity<>(new Response(ErrorCode.ERR_DATA_INVALID,"查找文件失败"), HttpStatus.BAD_REQUEST);
    }
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(deCryptId);
    map1.put("saveFlag",sealDocument.getPrintOrDownload());
    String fileBase64 = "";
    if(sign.equals("1")){
      fileBase64 = fileToBase64(Util.getSystemDictionary("sealBasePath"),sealDocument.getDocumentPath());
      map1.put("fileBase64", fileBase64);
    }else if(sign.equals("2"))
      fileBase64 = fileToBase64(Util.getSystemDictionary("sealedBasePath"),sealDocument.getSealedPath());
    map1.put("fileBase64", fileBase64);
    return new ResponseEntity<>(map1, HttpStatus.OK);
  }

  @RequestMapping(path ="/saveFile",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity saveFile(@RequestBody Map map) throws Exception {
    Long nowTime =System.currentTimeMillis();
    if (Util.isBlank(map.get("id").toString())){
      return new ResponseEntity<>(new Response(ErrorCode.ERR_DATA_INVALID,"查找文件失败"), HttpStatus.BAD_REQUEST);
    }
    if (Util.isBlank(map.get("pdFlag").toString())){
      return new ResponseEntity<>(new Response(ErrorCode.ERR_DATA_INVALID,"选择失败"), HttpStatus.BAD_REQUEST);
    }
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(map.get("id").toString());
    String tempDocumentName = sealDocument.getDocumentPath().substring(sealDocument.getDocumentPath().lastIndexOf("/")+1,sealDocument.getDocumentPath().length());
    base64ToFile(mkdir(Util.getSystemDictionary("sealedBasePath"))+"/"+tempDocumentName,map.get("fileBase64").toString());
    sealDocument.setPrintOrDownload(Integer.parseInt(map.get("pdFlag").toString()));
    if(map.get("pdFlag").toString().equals("1")||map.get("pdFlag").toString().equals("3")){
      sealDocument.setPrintTotalNum(Integer.parseInt(map.get("printNumber").toString()));
    }
    sealDocument.setSealedAt(nowTime/1000);
    sealDocument.setSealStatus(2);
    sealDocument.setUploadMode(1);
    sealDocument.setSealedPath(getMidPath()+"/"+tempDocumentName);
//		sealDocument.setSealedPath(Util.getSystemDictionary("sealedBasePath")+getMidPath()+"/"+tempDocumentName);
    saveSeal(map.get("sealJsonStr").toString(),map.get("documentId").toString(),map.get("sealManager").toString());
    //印章保存
    sealDocumentDao.save(sealDocument);
    String key = Util.getSystemDictionary("fileKey");
    DesUtil des = new DesUtil(key);
    String enCryptId = des.encrypt(sealDocument.getId());
    String redirectUrl = Util.getSystemDictionary("jumpUrl")+"/ESS/#/getInfo?fileId="+enCryptId;
    System.out.println(redirectUrl);
    //本地用
//		String redirectUrl = Util.getSystemDictionary("jumpUrl")+"/ESS/#/getInfo?fileId="+enCryptId;
    //测试用
    String result = addSendInfo(redirectUrl,sealDocument);
    if(result.equals("1")){
      sealDocument.setHandleResult(Integer.parseInt("1"));
      sealDocument.setReUpdateTime(nowTime/1000);
      sealDocumentDao.save(sealDocument);
      return new ResponseEntity<>(new Response(200,"保存成功"), HttpStatus.OK);
    }
    sealDocument.setHandleResult(Integer.parseInt("0"));
    sealDocument.setReUpdateTime(nowTime/1000);
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(500,"发送文件失败,请重新发送"), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @RequestMapping(path ="/saveUploadFile",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity saveUploadFile(@RequestBody Map map) throws Exception {
    Date date = null;
    DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    Long nowTime =System.currentTimeMillis();
    String tempDocumentName = UUID.randomUUID().toString();
    String sealDocumentName = map.get("realDocumentName").toString();
//		String finalName  = sealDocumentName.substring(sealDocumentName.lastIndexOf("\\")+1,sealDocumentName.length());
//		String sealDocumentType = sealDocumentName.substring(sealDocumentName.lastIndexOf(".")+1,sealDocumentName.length());
    String sealDocumentType = "pdf";
    SealDocument sealDocument = new SealDocument();

    sealDocument.setDocumentId(tempDocumentName);
    if(map.get("id").toString().equals("")||map.get("id").toString().equals(null)){
      sealDocument.setId(UUIDReduce.uuid());
    }else{
      sealDocument.setId(map.get("id").toString());
    }
    sealDocument.setReportTitle(map.get("reportTitle").toString());
    sealDocument.setReportPerson(map.get("reportPerson").toString());
    sealDocument.setApprovePerson(map.get("approvePerson").toString());
    sealDocument.setReceivePerson(map.get("receivePerson").toString());
    sealDocument.setDocumentName(sealDocumentName);
    date = format.parse(String.valueOf(map.get("reportDate")));
    Long time = date.getTime();
    sealDocument.setReportDate(time/1000);
    sealDocument.setSealedAt(nowTime/1000);
    sealDocument.setReportId(map.get("reportId").toString());
    sealDocument.setDeptAllName(map.get("reportDeptAllName").toString());
//        sealDocument.setDeptAllName(departmentDao.findDepById(map.get("reportDeptId").toString()).getAllName());
    sealDocument.setSealManager(map.get("sealManager").toString());
    sealDocument.setDocumentType(sealDocumentType);
    sealDocument.setPrintOrDownload(Integer.parseInt(map.get("pdFlag").toString()));
    if(map.get("pdFlag").toString().equals("1")||map.get("pdFlag").toString().equals("3")){
      sealDocument.setPrintTotalNum(Integer.parseInt(map.get("printNumber").toString()));
    }

    base64ToFile(mkdir(Util.getSystemDictionary("sealedBasePath"))+"/"+tempDocumentName+"."+sealDocumentType,map.get("fileBase64").toString());

    sealDocument.setSealedPath(getMidPath()+"/"+tempDocumentName+"."+sealDocumentType);
    sealDocument.setSealStatus(2);
    sealDocument.setUploadMode(2);
    sealDocument.setSealedAt(nowTime/1000);
    if(map.get("remark")!=null&&(map.get("remark").toString().equals("")||map.get("remark").toString().equals(null))){
      sealDocument.setRemark("");
    }
    saveSeal(map.get("sealJsonStr").toString(),tempDocumentName,map.get("sealManager").toString());
    //印章保存
    sealDocumentDao.save(sealDocument);
    String key = Util.getSystemDictionary("fileKey");
    DesUtil des = new DesUtil(key);
    String enCryptId = des.encrypt(sealDocument.getId());
    String redirectUrl = Util.getSystemDictionary("jumpUrl")+"/ESS/#/getInfo?fileId="+enCryptId;
    String result = addInfo(redirectUrl,sealDocument);
    if(result.equals("1")){
      sealDocument.setHandleResult(Integer.parseInt("1"));
      sealDocument.setReUpdateTime(nowTime/1000);
      sealDocumentDao.save(sealDocument);
      return new ResponseEntity<>(new Response(200,"保存成功"), HttpStatus.OK);
    }
    sealDocument.setHandleResult(Integer.parseInt("0"));
    sealDocument.setReUpdateTime(nowTime/1000);
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(500,"发送文件失败,请重新发送"), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @RequestMapping(path ="/getUserNames",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity getUserNames(@RequestBody String name) throws Exception {
    List<User> list = userDao.findUserByName(name);
    List<String> list1 = new ArrayList<>();
    for(int i=0;i<list.size();i++){
      list1.add(list.get(i).getName());
    }
    if(list1.size()==0||list1 == null){
      return new ResponseEntity<>(new Response(500,"无此人信息"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new ResponseEntity<>(list1,HttpStatus.OK);

  }

  @RequestMapping(path ="/reSendFile/{id}",method = RequestMethod.GET, produces = "application/json")
  public ResponseEntity reSendFile(@PathVariable String id) throws Exception {
    Long nowTime = null;
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(id);
    String key = Util.getSystemDictionary("fileKey");
    DesUtil des = new DesUtil(key);
    String enCryptId = des.encrypt(sealDocument.getId());
    String redirectUrl = Util.getSystemDictionary("jumpUrl")+"/ESS/#/getInfo?fileId="+enCryptId;
    String result = addInfo(redirectUrl,sealDocument);
    if(result.equals("1")){
      nowTime = System.currentTimeMillis();
      sealDocument.setHandleResult(Integer.parseInt("1"));
      sealDocument.setReUpdateTime(nowTime/1000);
      sealDocumentDao.save(sealDocument);
      return new ResponseEntity<>(new Response(200,"保存成功"), HttpStatus.OK);
    }
    nowTime =System.currentTimeMillis();
    sealDocument.setHandleResult(Integer.parseInt("0"));
    sealDocument.setReUpdateTime(nowTime/1000);
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(500,"保存失败"), HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @RequestMapping(path = "/verifyLogin", method = RequestMethod.POST ,produces = "application/json")
  public ResponseEntity verifyLogin(@RequestBody Map map) throws Exception {
    String userId = map.get("userId").toString();
    String currentTime= map.get("currentTime").toString();
    String desKey = Util.getSystemDictionary("loginKey");
    DesUtil des = new DesUtil(desKey);
    String deCryptUserId = des.decrypt(userId);
    DesUtil des1 = new DesUtil(desKey);
    String deCryptCurrentTime = des1.decrypt(currentTime);

    Long nowTime =System.currentTimeMillis();
    User user = userDao.findUserByLoginId(deCryptUserId);
    if (user == null) {
      return new ResponseEntity<>(new Response(500,"用户不存在"), HttpStatus.INTERNAL_SERVER_ERROR);
    }else if(nowTime-Long.valueOf(deCryptCurrentTime)>86400000){
      return new ResponseEntity<>(new Response(500,"登录超时"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new ResponseEntity<>(user, HttpStatus.OK);
  }

  @RequestMapping(path = "/updatePrint", method = RequestMethod.POST ,produces = "application/json")
  public ResponseEntity updatePrint(@RequestBody Map map) throws Exception {
    String id = map.get("id").toString();
    String printNumber= map.get("printNumber").toString();
    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(id);
    sealDocument.setPrintedNum(Integer.valueOf(printNumber)+sealDocument.getPrintedNum());
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(200,"打印成功"), HttpStatus.OK);
  }

  @RequestMapping(path = "/getQRcode", method = RequestMethod.POST ,produces = "application/json")
  public ResponseEntity getQRcode(@RequestBody Map map) throws Exception {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    Map map1 = new HashMap();
    String QRcode = "";
    String id = map.get("id").toString();
    if(id.equals("")){
      String reportId = map.get("reportId").toString();
      String sealDocumentName = map.get("realDocumentName").toString();
      String documentName  = sealDocumentName.substring(sealDocumentName.lastIndexOf("\\")+1,sealDocumentName.length());
      String finalName = documentName.substring(0,documentName.lastIndexOf("."));
      String reportDate = df.format(new Date());
      String uid= UUIDReduce.uuid();
      String oriData = uid+";"+reportId+";"+finalName+";"+reportDate;
      QRcode = ECCUtil.encryptByPublicKey(oriData,Util.getSystemDictionary("publicKey"));
      map1.put("id",uid);
      map1.put("QRcode",QRcode);
    }else{
      String reportId = map.get("reportId").toString();
      String documentName = map.get("documentName").toString();
      String reportDate = df.format(new Date());
      String oriData = id+";"+reportId+";"+documentName+";"+reportDate;
      QRcode = ECCUtil.encryptByPublicKey(oriData,Util.getSystemDictionary("publicKey"));
      map1.put("QRcode",QRcode);
    }
    return new ResponseEntity<>(map1,HttpStatus.OK);
  }


  @RequestMapping(path ="/updateFile",method = RequestMethod.POST, produces = "application/json")
  public ResponseEntity updateFile(@RequestBody Map<String,String> map) throws Exception {
    String fileBase64 = map.get("fileBase64");
    System.out.println(fileBase64);
    String fileName = UUID.randomUUID()+".pdf";
    String fileName1 = UUID.randomUUID()+".pdf";
    base64ToFile(mkdir2(Util.getSystemDictionary("sealBasePath"))+"/"+fileName,fileBase64);
    base64ToFile(mkdir(Util.getSystemDictionary("sealedBasePath"))+"/"+fileName1,fileBase64);

    User user = userDao.findOne(map.get("userId"));
    SealDocument sealDocument1 = new SealDocument();
    String departmentId = map.get("departmentId");
    Department de = departmentDao.findDepById(departmentId);

    SealDocument sealDocument = sealDocumentDao.findSealDocuemntById(map.get("sealId"));
    sealDocument1.setApprovePerson(sealDocument.getApprovePerson());
    sealDocument1.setDeptAllName(de.getAllName());
    sealDocument1.setDocumentId(sealDocument.getDocumentId());
    sealDocument1.setDocumentName(sealDocument.getDocumentName());
    sealDocument1.setDocumentType(sealDocument.getDocumentType());
    sealDocument1.setHandleResult(sealDocument.getHandleResult());
    sealDocument1.setPrintedNum(sealDocument.getPrintedNum());
    sealDocument1.setPrintOrDownload(sealDocument.getPrintOrDownload());
    sealDocument1.setPrintTotalNum(sealDocument.getPrintTotalNum());
    sealDocument1.setReceivePerson(sealDocument.getReceivePerson());
    sealDocument1.setRemark(sealDocument.getRemark());
    sealDocument1.setReportDate(sealDocument.getReportDate());
    sealDocument1.setReportId(sealDocument.getReportId());
    sealDocument1.setReportDeptId(sealDocument.getReportDeptId());
    sealDocument1.setReportPerson(sealDocument.getReportPerson());
    sealDocument1.setReportTitle(sealDocument.getReportTitle());
    sealDocument1.setReUpdateTime(sealDocument.getReUpdateTime());
    sealDocument1.setSealedAt(System.currentTimeMillis()/1000);
//		String tempDocumentName = sealDocument.getDocumentPath().substring(sealDocument.getDocumentPath().lastIndexOf("/")+1,sealDocument.getDocumentPath().length());
//		sealDocument1.setDocumentPath(Util.getSystemDictionary("sealedBasePath")+getMidPath()+"/"+tempDocumentName);
    sealDocument1.setDocumentPath(fileName);
    sealDocument1.setStatus((byte)1);
    sealDocument1.setSealedPath("");
    sealDocument1.setSealManager(user.getLoginId());
    sealDocument1.setSealStatus(1);
    sealDocument1.setUploadMode(sealDocument.getUploadMode());
    sealDocumentDao.save(sealDocument1);

    sealDocument.setSealStatus(2);
    sealDocument.setSealedAt(System.currentTimeMillis()/1000);
    //sealDocument.setSealedPath(getMidPath()+"/"+tempDocumentName+"."+sealDocumentType);
    sealDocument.setSealedPath(getMidPath()+"/"+fileName1);
    sealDocumentDao.save(sealDocument);
    return new ResponseEntity<>(new Response(200,"OK"), HttpStatus.OK);
  }


  public String fileToBase64(String basePath, String filePath) throws IOException {
    File file = new File(basePath + filePath);
    FileInputStream inputFile = new FileInputStream(file);
    byte[] buffer = new byte[(int) file.length()];
    inputFile.read(buffer);
    inputFile.close();
    return new BASE64Encoder().encode(buffer);
  }

  public void base64ToFile(String finalPath, String fileBase64){
    byte[] buffer;
    try {
      buffer = new BASE64Decoder().decodeBuffer(fileBase64);
      FileOutputStream out = new FileOutputStream(finalPath);
      out.write(buffer);
      out.close();
    } catch (Exception e) {
      throw new RuntimeException("base64字符串异常或地址异常\n" + e.getMessage());
    }
  }

  public String mkdir(String basePath){
    Calendar c = Calendar.getInstance();
    int year = c.get(Calendar.YEAR);
    int month = c.get(Calendar.MONTH) + 1;
    int day = c.get(Calendar.DATE);
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

  public String mkdir2(String basePath){
    String muluStr = basePath;
    File filemulu = new File(muluStr);
    if (!filemulu.exists() && !filemulu.isDirectory()) {
      filemulu.mkdirs();
    }
    return muluStr;
  }

  public String getMidPath(){
    Calendar c = Calendar.getInstance();
    int year = c.get(Calendar.YEAR);
    int month = c.get(Calendar.MONTH) + 1;
    int day = c.get(Calendar.DATE);
    String yearStr = Integer.toString(year);
    String monthStr = Integer.toString(month);
    String dayStr = Integer.toString(day);
    String filePath = yearStr + "/" + monthStr + "/" + dayStr;
    return filePath;
  }

  public String addInfo(String redirectUrl, SealDocument sealDocument) {
    String url = Util.getSystemDictionary("asmxUrl");//提供接口的地址
    String soapaction = Util.getSystemDictionary("soapaction");   //域名，这是在server定义的
    Service service = new Service();
    String message = "1";
    try {
      Call call = (Call) service.createCall();
      call.setTargetEndpointAddress(url);
      call.setOperationName(new QName(soapaction, "AddInfo")); //设置要调用哪个方法
      call.addParameter(new QName(soapaction, "XMLInfo"), //设置要传递的参数
              org.apache.axis.encoding.XMLType.XSD_STRING,
              javax.xml.rpc.ParameterMode.IN);
      call.setReturnType(new QName(soapaction, "AddInfo"), String.class); //要返回的数据类型（自定义类型）
      call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//（标准的类型）
      call.setUseSOAPAction(true);
      call.setSOAPActionURI(soapaction + "AddInfo");
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
      long lt = sealDocument.getUpdatedAt() * 1000;
      Date date = new Date(lt);
      User user = userDao.findOneUserByName(sealDocument.getReceivePerson());
      if(user == null||user.equals("")){
        message = "用户不存在";
      }
      String i = " <ZDATA><ITEM><PT_USERID>"+user.getLoginId()+"</PT_USERID>"+"<CODE>"+sealDocument.getReportId()+"</CODE>"+"<TITLE>"+sealDocument.getReportTitle()+"</TITLE>" +
              "<ANAME>"+sealDocument.getDocumentName()+"</ANAME>" + "<FTIME>"+simpleDateFormat.format(date)+"</FTIME>" +
              "<TYPE>"+sealDocument.getDocumentType()+"</TYPE><FURL>"+redirectUrl+"</FURL></ITEM></ZDATA>";
      String xmlStr = (String) call.invoke(new Object[]{i});
      System.out.println("发送的数据串-------------------"+i);
      Document document = DocumentHelper.parseText(xmlStr);
      Element root = document.getRootElement();
      Iterator<Element> e1 = root.elementIterator();
      Element e2 = e1.next();
      message = e2.getText().trim();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return message;
  }

  public String addSendInfo(String redirectUrl, SealDocument sealDocument) {
    String url = Util.getSystemDictionary("asmxUrl");//提供接口的地址
    String soapaction = Util.getSystemDictionary("soapaction");   //域名，这是在server定义的
    Service service = new Service();
    String message = "1";
    try {
      Call call = (Call) service.createCall();
      call.setTargetEndpointAddress(url);
      call.setOperationName(new QName(soapaction, "AddInfo")); //设置要调用哪个方法
      call.addParameter(new QName(soapaction, "XMLInfo"), //设置要传递的参数
              org.apache.axis.encoding.XMLType.XSD_STRING,
              javax.xml.rpc.ParameterMode.IN);
      call.setReturnType(new QName(soapaction, "AddInfo"), String.class); //要返回的数据类型（自定义类型）
      call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);//（标准的类型）
      call.setUseSOAPAction(true);
      call.setSOAPActionURI(soapaction + "AddInfo");
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
      long lt = sealDocument.getUpdatedAt() * 1000;
      Date date = new Date(lt);
      String i = " <ZDATA><ITEM><PT_USERID>"+sealDocument.getReceivePerson()+"</PT_USERID>"+"<CODE>"+sealDocument.getReportId()+"</CODE>"+"<TITLE>"+sealDocument.getReportTitle()+"</TITLE>" +
              "<ANAME>"+sealDocument.getDocumentName()+"</ANAME>" + "<FTIME>"+simpleDateFormat.format(date)+"</FTIME>" +
              "<TYPE>"+sealDocument.getDocumentType()+"</TYPE><FURL>"+redirectUrl+"</FURL></ITEM></ZDATA>";
      String xmlStr = (String) call.invoke(new Object[]{i});
      System.out.println("发送的数据串-------------------"+i);
      Document document = DocumentHelper.parseText(xmlStr);
      Element root = document.getRootElement();
      Iterator<Element> e1 = root.elementIterator();
      Element e2 = e1.next();
      message = e2.getText().trim();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return message;
  }

  public void saveSeal(String str, String documentId, String sealManager){
    List<SealLog> list= JSONObject.parseArray(str, SealLog.class);
    Map<String, Integer> map = new HashMap<>();
    Map<String, String> map1 = new HashMap<>();
    for(int i=0;i<list.size();i++){
      String sealId = list.get(i).getSealId();
      String sealName = list.get(i).getSealName();
      if(map.containsKey(sealId)){
        map.put(sealId,map.get(sealId)+1);
      }else{
        map.put(sealId,1);
      }
      map1.put(sealId,sealName);
    }
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      SealLog sealLog = new SealLog();
      sealLog.setSealId(entry.getKey());
      sealLog.setSealCount(entry.getValue());
      sealLog.setSealName(map1.get(entry.getKey()));
      sealLog.setDocuemntId(documentId);
      sealLog.setSealManager(sealManager);
      sealLogDao.save(sealLog);
    }
  }

  public HSSFWorkbook getHSSFWorkbook(String sheetName, String []title, String [][]values, HSSFWorkbook wb){
    if(wb == null){
      wb = new HSSFWorkbook();
    }
    HSSFSheet sheet = wb.createSheet(sheetName);
    sheet.setDefaultColumnWidth(20);
    HSSFRow row = sheet.createRow(0);
    HSSFCellStyle cellStyle = wb.createCellStyle();
    cellStyle.setAlignment(HorizontalAlignment.CENTER);
    HSSFCell cell = null;
    for(int i=0;i<title.length;i++){
      cell = row.createCell(i);
      cell.setCellValue(title[i]);
      cell.setCellStyle(cellStyle);
    }

    //创建内容
    for(int i=0;i<values.length;i++){
      row = sheet.createRow(i + 1);
      for(int j=0;j<values[i].length;j++){
        //将内容按顺序赋给对应的列对象
        row.createCell(j).setCellValue(values[i][j]);
      }
    }
    return wb;
  }



  @Autowired
  SealDocumentDao sealDocumentDao;

  @Autowired
  DepartmentDao departmentDao;

  @Autowired
  SealLogDao sealLogDao;

  @Autowired
  UserDao userDao;


}

