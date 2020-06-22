package com.dianju.core;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUploadAndDownLoadUtil {
	private static final Logger log = LoggerFactory.getLogger(FileUploadAndDownLoadUtil.class);
	 /**
     * http下载文件
     * @param fileName 文件名
     * @param downPathHttp 下载路径
     * @param beginTime 日志标识
     * @param fileMsg 返回消息
     * @return ok成功 其他失败
     */
	public static String httpDownFile(String fileName,String downPathHttp,String beginTime,Map fileMsg){
    	//String downPathHttp= downPath+"/downPathHttp";
    	log.info(beginTime+":http下载开始");
    	String fname=new Date().getTime()+"";
    	fname=beginTime+"-"+fname;
    	//截取文件后缀
    	int m=fileName.lastIndexOf(".");
    	String fileSuffix;
    	if(m!=-1) {
			fileSuffix = fileName.substring(m);
			if(!fileSuffix.endsWith("pdf")){
				fileSuffix =".doc";
			}
		}else {
    		log.info(fname+":文档后缀错误FILE_PATH:"+fileName);
    		return "文档后缀错误FILE_PATH:"+fileName;
    	}
    	log.info(fname+":FILE_PATH:"+fileName);
    	String saveFileName=downPathHttp+"/"+fname+fileSuffix;
    	log.info(fname+":http文档保存路径："+saveFileName);
    	HttpClient client = new HttpClient();
        GetMethod get = null;
        BufferedOutputStream  outStream=null;
        try {
			//String  a  =URLEncoder.encode(fileName);
		    get = new GetMethod(fileName);
			//System.out.println(a);
			int i=client.executeMethod(get);
			//int  i=500;
			log.info("http连接i----------"+i);
			if (200 == i) {
		    	log.info(fname+":http请求成功,开始下载...");
			    outStream = new BufferedOutputStream(new FileOutputStream(saveFileName));
			    outStream.write(get.getResponseBody());
			    outStream.flush();
			    log.info(fname+":http文档下载成功 "+saveFileName);
			    fileMsg.put("fileUrl", saveFileName);
       		 	fileMsg.put("fileName", fname);
       		 	fileMsg.put("fileSuffix", fileSuffix);
                return "ok";  
		    }else{
		    	log.info(fname+":http请求错误 地址:"+fileName+" http返回值:"+i);
		    	return "http请求错误 地址:"+fileName+" http返回值:"+i;
		    }
		} catch (Exception e) {
			log.info(fname+":http下载失败："+e.getMessage());
			return "http下载失败："+e.getMessage();
		}finally{
			try {
				outStream.close();
			} catch (Exception e) {
				
			}
			get.releaseConnection();
			client.getHttpConnectionManager().closeIdleConnections(0);
		}
    } 
	 /**
	    * ftp建立连接
	    * @param ftpClient ftp客户端
	    * @param ftpEncoding ftp编码格式（可“”null 默认GBK）
	    * @param ftpAddress ftp地址
	    * @param ftpPort ftp端口 （可“”null 默认GBK）
	    * @param ftpUser ftp用户名
	    * @param ftpPsd ftp密码
	    * @param beginTime
	    * @return ok成功 其他错误信息
	    */
		protected static String ftpConnect(FTPClient ftpClient,String ftpEncoding, String ftpAddress,String ftpPort,String ftpUser,String ftpPsd,String beginTime){
		   try {
			   //设置ftp编码格式
			   	if(ftpEncoding!=null&&!"".equals(ftpEncoding)){
			   		ftpClient.setControlEncoding(ftpEncoding); 
			   	}else{
			   		ftpClient.setControlEncoding("GBK"); 
			   	}
			    //连接ftp服务器
				if (ftpPort==null || ftpPort.equals("")) {
					ftpClient.connect(ftpAddress);
					log.info(beginTime+":连接ftp服务器成功："+ftpAddress+":"+("".equals(ftpPort)?"21":ftpPort)+"   ");
				} else {
					ftpClient.connect(ftpAddress, Integer.parseInt(ftpPort));
				}
				int reply = ftpClient.getReplyCode();  
	            if (!FTPReply.isPositiveCompletion(reply)) {  
	            	log.info(beginTime+":连接ftp服务器失败："+ftpAddress+":"+("".equals(ftpPort)?"21":ftpPort)+"   ");
	     			return "连接ftp服务器失败："+ftpAddress+":"+("".equals(ftpPort)?"21":ftpPort)+"   ";
	     		}
	            //ftp登录
	        
	            
	             if(!ftpClient.login(ftpUser, ftpPsd)){
	            	 log.info(beginTime+":ftp服务器用户名、密码错误："+ftpAddress+":"+("".equals(ftpPort)?"21":ftpPort)+"   username："+ftpUser+" password："+ftpPsd);
	      			 return "ftp服务器用户名、密码错误："+ftpAddress+":"+("".equals(ftpPort)?"21":ftpPort)+"   username："+ftpUser+" password："+ftpPsd;
	             }
	            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);    
	    		ftpClient.enterLocalPassiveMode(); 
	    		return "ok";
		   } catch (Exception e) {
			    log.info(beginTime+":ftp登录失败 "+e.getMessage());
			    e.printStackTrace();
				return "ftp登录失败:"+e.getMessage();
		   }
		   
	   }
		/**
	    * 断开ftp连接
	    * @param ftpClient ftp客户端
	    */
	   protected static void ftpDisconnect(FTPClient ftpClient){
		   try {
				ftpClient.logout();
			} catch (Exception e) {
				
			}
			try {
				ftpClient.disconnect();
			} catch (IOException e) {
				
			}
	   }
	   /**
	    * ftp文件上传
	    * @param ftpEncoding
	    * @param ftpAddress
	    * @param ftpPort
	    * @param ftpUser
	    * @param ftpPwd
	    * @param localPath
	    * @param uploadPath
	    * @param beginTime
	    * @return ok成功 其他失败原因
	    */
	   protected static String ftpUpFile(String ftpEncoding, String ftpAddress,String ftpPort,String ftpUser,String ftpPwd,String localPath,String uploadPath,String beginTime){
		   FTPClient ftpClient=new FTPClient();
		   String ftpLoginRet= ftpConnect(ftpClient,ftpEncoding,  ftpAddress,ftpPort,ftpUser,ftpPwd, beginTime);
			 if(!"ok".equals(ftpLoginRet)){
				 return ftpLoginRet;
			 }
			 InputStream in = null;
			try {
				in = new FileInputStream(localPath);
				if(ftpClient.storeFile(uploadPath, in)){
					 return "ok";
				}else{
					 log.info(beginTime+":ftp文件上传失败"+uploadPath);
					 return "ftp文件上传失败"+uploadPath;
				}
			}catch (FileNotFoundException e) {
				 log.info(beginTime+":ftp文件上传失败"+e.getMessage());
				 return "ftp文件上传失败"+e.getMessage();
			} catch(IOException e1){
				 log.info(beginTime+":ftp文件上传失败"+e1.getMessage());
				 return "ftp文件上传失败"+e1.getMessage();
			}finally{
				ftpDisconnect(ftpClient);
				 try {
					in.close();
				 } catch (IOException e) {
				 }
			}  
	   }
	   /**
	    * ftp下载文件
	    * @param ftpEncoding
	    * @param ftpAddress
	    * @param ftpPort
	    * @param ftpUser
	    * @param ftpPwd
	    * @param ftpDownPath
	    * @param beginTime
	    * @param fileMsg 下载后文件的信息（传空Map，调用结束后读取）
	    * @return ok成功 其他失败
	    */
	   protected static String ftpDownFile(String ftpEncoding, String ftpAddress,String ftpPort,String ftpUser,String ftpPwd,String ftpDownPath,String savePath,String beginTime,Map fileMsg){
	     //	String downPathFtp= downPath+"/downPathFtp";
	     	log.info(beginTime+":ftp下载开始");
	     	String fname=new Date().getTime()+"";
	     	fname=beginTime+"-"+fname;
	   
	     	//截取文件后缀
	     	int i=ftpDownPath.lastIndexOf(".");
	     	String fileSuffix;
	     	if(i!=-1)
	     		fileSuffix=ftpDownPath.substring(i);
	     	else {
	     		log.info(fname+":文档后缀错误FTP_DOWNPATH:"+ftpDownPath);
	     		return "文档后缀错误FTP_DOWNPATH:"+ftpDownPath;
	     	}
	     	log.info(fname+":FTP_DOWNPATH:"+ftpDownPath);
	     	String saveFileName=savePath+"/"+fname+fileSuffix;
	     	log.info(fname+":ftp文档保存路径："+saveFileName);
	     	FTPClient ftpClient=new FTPClient();
	     	try {
	     		//连接ftp服务器
	     		
	     		
	     		 String ftpLoginRet= ftpConnect(ftpClient,ftpEncoding,  ftpAddress,ftpPort,ftpUser,ftpPwd, fname);
	     		 if(!"ok".equals(ftpLoginRet)){
	     			 return ftpLoginRet;
	     		 }	
	      		 BufferedOutputStream  outStream=null;
	              try {
	             	//下载文件
	             	 outStream = new BufferedOutputStream(new FileOutputStream(saveFileName));
	             	 boolean success= ftpClient.retrieveFile(ftpDownPath, outStream);
	             	 if (success == true) {
	             		 outStream.flush();
	             		 log.info(fname+":ftp文档下载成功 "+saveFileName);
	             		 fileMsg.put("fileUrl", saveFileName);
	             		 fileMsg.put("fileName", fname);
	             		 fileMsg.put("fileSuffix", fileSuffix);
	             		 
	                      return "ok";  
	                  }else{
	                 	 log.info(fname+":ftp文档下载失败(文件不存在)FTP_DOWNPATH:"+ftpDownPath);
	                 	 return "ftp文档下载失败FTP_DOWNPATH:"+ftpDownPath;
	                  }
	 			} catch (Exception e) {
	 				log.info(fname+"文档下载失败"+e.getMessage());
	 				return fname+"文档下载失败"+e.getMessage();
	 			}finally{
	 				try {
	 					outStream.close();
	 				} catch (Exception e2) {
	 				}
	 			}
	           
	 		} catch (Exception e) {
	 			log.info(beginTime+":ftp文件下载失败"+e.getMessage());
	 			return "ftp文件下载失败"+e.getMessage();
	 		}finally{
	 			log.info(beginTime+":ftp下载结束");
	 			ftpDisconnect(ftpClient);
	 		}
	     	
	     }
	   public String ftpFileVarfy(String ftpEncoding,String ftpAddress,String ftpPort,String ftpUser,String ftpPwd,String ftpDownPath,String savePath,String beginTime,Map fileMsg){
		  String reg =  ftpDownFile(ftpEncoding, ftpAddress, ftpPort, ftpUser, ftpPwd, ftpDownPath, savePath, beginTime, fileMsg);
		   return reg;
	   }
	   
}






