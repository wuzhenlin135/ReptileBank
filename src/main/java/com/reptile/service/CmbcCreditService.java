package com.reptile.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.By.ByClassName;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import com.hoomsun.keyBoard.HttpWatchUtil;
import com.hoomsun.keyBoard.SendKeys;
import com.reptile.analysis.CmbcCreditAnalysis;
import com.reptile.util.CountTime;
import com.reptile.util.DriverUtil;
import com.reptile.util.JsonUtil;
import com.reptile.util.PushSocket;
import com.reptile.util.PushState;
import com.reptile.util.Resttemplate;
import com.reptile.util.SimpleHttpClient;
import com.reptile.util.application;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * 
 * @ClassName: CmbcCreditService
 * @Description: TODO (民生信用卡)
 * @author: xuesongcui
 * @date 2018年1月2日
 *
 */
@Service("cmbcCreditService")
public class CmbcCreditService {

	Logger logger = Logger.getLogger(CmbcCreditService.class);


	/**
	 * 民生信用卡
	 * 
	 * @param number
	 * @param pwd
	 * @param banktype
	 * @param idcard
	 * @param UUID
	 * @param timeCnt
	 * @return
	 * @throws Exception
	 */
	public synchronized Map<String, Object> doLogin(HttpServletRequest request,String number, String pwd,
			String banktype, String idcard, String uuid, String timeCnt)
			throws Exception {
		
		boolean isok = CountTime.getCountTime(timeCnt);
		if (isok == true) {
			PushState.state(idcard, "bankBillFlow", 100);
		}
		
		Map<String, Object> map = new HashMap<String, Object>(16);
		PushSocket.push(map, uuid, "1000", "民生银行登录中");
		int flags = 1;
		WebDriver driver = null;
		try {
			WebElement errorinfo = null;
			try {
				logger.warn("########【民生信用卡########登陆开始】########【用户名：】"
						+ number + "【密码：】" + pwd+"【身份证号：】"+idcard);
				logger.warn("########【登陆开始】########【身份证号：】"+idcard);
				driver = DriverUtil.getDriverInstance("ie");
				Thread.sleep(1000);
				
				//打开登录也页面
				driver.get("https://nper.cmbc.com.cn/pweb/static/login.html");
				
				Boolean flag = DriverUtil.waitByTitle("中国民生银行个人网上银行", driver, 15);
				if(flag){
					
					Thread.sleep(1000);
					/* 获得输入元素 */
					WebElement elements = driver.findElement(By.id("writeUserId"));
					elements.sendKeys(number);
					/* 执行换号 */
					Thread.sleep(1000);
					//SendKeys.sendStr(1180, 380+15, pwd);
					SendKeys.sendStr(1180, 380+80, pwd);//本地
					Thread.sleep(1000);

					WebElement loginButton = driver.findElement(By.id("loginButton"));
					WebElement webElement = driver.findElement(By.id("_tokenImg"));

					String src = "src";
					if (webElement.getAttribute(src) == null
							|| "".equals(webElement.getAttribute(src))) {
						/* 不需要验证码直接提交 */
					} else {
						/* 需要验证码进行打码 */
						logger.warn("########【需要打印验证码】########【身份证号：】"+idcard);
						WebElement imgEle = driver.findElement(By.id("_tokenImg"));
		                String imageCode = CmbSavingsService.imageGet(imgEle, driver,request);
						driver.findElement(By.id("_vTokenName")).sendKeys(imageCode);
					}
					
					loginButton.click();
					//调出httpwatch
					HttpWatchUtil.openHttpWatch();
					errorinfo = driver.findElement(By.className("alert-heading"));
					
				}else{
					throw new Exception();
				}
				
			} catch (Exception e) {
				logger.error("网络异常，登录失败",e);
				logger.warn("########【未登陆成功，进入try-catch】########【原因：】网络异常，登录失败【身份证号：】"+idcard);
				PushSocket.push(map, uuid, "3000", "网络异常，登录失败【身份证号：】"+idcard);
				if (isok == true) {
					PushState.state(idcard, "bankBillFlow", 200,"网络异常，登录失败");
				}else {
					PushState.stateX(idcard, "bankBillFlow", 200,"网络异常，登录失败");
				}
				map.put("errorCode", "0001");
				map.put("errorInfo", "网络异常，登录失败");
				DriverUtil.close(driver);
				logger.warn("----民生信用卡------errorCode："+map.get("errorCode")+"-----errorInfo："+map.get("errorInfo"));
				return map;
			}
			if (!"".equals(errorinfo.getText())) {
				logger.warn("########【未登陆成功】########【原因：】"+errorinfo.getText()+"【身份证号：】"+idcard);
				PushSocket.push(map, uuid, "3000", errorinfo.getText());
				if (isok == true) {
					PushState.state(idcard, "bankBillFlow", 200,errorinfo.getText());
				}else {
					PushState.stateX(idcard, "bankBillFlow", 200,errorinfo.getText());
				}
				map.put("errorCode", "0001");
				map.put("errorInfo", errorinfo.getText());
				logger.warn("----民生信用卡------errorCode："+map.get("errorCode")+"-----errorInfo："+map.get("errorInfo"));
				DriverUtil.close(driver);
				return map;
			} else {
				logger.warn("########【登陆成功】########【身份证号：】"+idcard);
				Boolean flag = DriverUtil.waitByTitle("中国民生银行个人网银", driver, 15);
				if(flag){					
					String title = "中国民生银行个人网银";
					if (driver.getTitle().contains(title)) {
						PushSocket.push(map, uuid, "2000", "民生银行登陆成功");
						flags = 2;
						Thread.sleep(2000);
						PushSocket.push(map, uuid, "5000", "民生银行数据获取中");
						logger.warn("########【开始获取数据】########【身份证号：】"+idcard);
						
						Map<String, Object> data = new HashMap<String, Object>(16);
						data.put("bankList", this.getDetail(driver));
						PushSocket.push(map, uuid, "6000", "民生银行数据获取成功");
						logger.warn("########【数据获取成功】########【身份证号：】"+idcard);
						flags = 3;
			            
			            map.put("data", data);
			            map.put("backtype", "CMBC");
			            map.put("idcard", idcard);
			            map.put("userAccount",number);
			            map.put("bankname", "民生银行信用卡");
			            map.put("isok", isok);
			            logger.warn("########【数据获取成功】########【数据为】" + map.toString());
						
						logger.warn("########【开始推送】########【身份证号：】"+idcard);
						map = new Resttemplate().SendMessageX(map, application.sendip + "/HSDC/BillFlow/BillFlowByreditCard", idcard,uuid);
						logger.warn("########【推送完成】########【身份证号：】"+idcard+"数据中心返回结果："+map.toString());
					} else {
						PushSocket.push(map, uuid, "3000", "网络异常，登陆失败");
						if (isok == true) {
							PushState.state(idcard, "bankBillFlow", 200,"网络异常，登陆失败");
						}else {
							PushState.stateX(idcard, "bankBillFlow", 200,"网络异常，登陆失败");
						}
						map.put("errorCode", "0001");
						map.put("errorInfo", "网络异常，登陆失败");
					}
				}else{
					throw new Exception();
				}
			}
		} catch (Exception e) {
			logger.error("民生信用卡查询失败", e);
			if(flags==1) {
				PushSocket.push(map, uuid, "3000", "网页数据没有找到");
				map.put("errorCode", "0001");
			}else if(flags==2) {
				PushSocket.push(map, uuid, "7000", "网页数据没有找到");
				map.put("errorCode", "0002");
			}else if(flags==3) {
				PushSocket.push(map, uuid, "9000", "网页数据没有找到");
				map.put("errorCode", "0003");
			}
			if (isok == true) {
				PushState.state(idcard, "bankBillFlow", 200,"网页数据没有找到");
			}else {
				PushState.stateX(idcard, "bankBillFlow", 200,"网页数据没有找到");
			}
			logger.warn("########【进入try-catch】########【原因：】网页数据没有找到【身份证号：】"+idcard);
			map.put("errorInfo", "网页数据没有找到");
		} finally {
			DriverUtil.close(driver);
		}
		logger.warn("------------民生信用卡-----------查询结束----------------返回信息为：" + map.toString() + "-------------");
		return map;
	}

	
	
	
	/**
	 * 通过发包的形式获取账单
	 * @param driver 
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<List<String>> getDetail(WebDriver driver) throws Exception {

		//发包拿到信用额度
		 
		 String url="https://nper.cmbc.com.cn/pweb/CreditUserInfoQryTrans.do";
         
		 String jsession = HttpWatchUtil.getCookie("JSESSION");
		 System.out.println(jsession);

         Map<String,Object> params11=new HashMap<String, Object>();
         params11.put("Type", null);
         params11.put("ISetPws", null);
         Map<String,String> headers11=new HashMap<String, String>();
         headers11.put("Cookie", jsession);
         headers11.put("Host", "nper.cmbc.com.cn");
         headers11.put("Referer", "https://nper.cmbc.com.cn/pweb/static/main.html");
         String response11=SimpleHttpClient.post(url,params11, headers11);
         JSONObject datestr = JSONObject.fromObject(response11);
 		 JSONArray date = JSONArray.fromObject(datestr.get("List"));
 		 //信用额度
 		 String CreTLmit=JSONObject.fromObject(date.get(0)).get("CreTLmit").toString();
         
 		 
		List<List<String>> list = new ArrayList<List<String>>(16);

//		String jsession = HttpWatchUtil.getCookie("JSESSION");
		Map<String, String> headers = new HashMap<String, String>(16);
		headers.put("Cookie", jsession);
		headers.put("Referer", "https://nper.cmbc.com.cn/pweb/static/main.html");
		headers.put("Host", "nper.cmbc.com.cn");

		Map<String, Object> params = new HashMap<String, Object>(16);
		// 请求1
		String response = SimpleHttpClient.post("https://nper.cmbc.com.cn/pweb/CreditCheckBillQry.do", params,headers);
		logger.warn("------请求1------response：" + response);
		params.put("CreditAcType", "0010");
		// 请求2
		response = SimpleHttpClient.post(
				"https://nper.cmbc.com.cn/pweb/CreditCardServiceTypeQry.do",
				params, headers);
		logger.warn("------请求2------response：" + response);
		
		String billDay = (String) JsonUtil.getJsonValue1(response, "BillDay");
		int month = Integer.parseInt((String)JsonUtil.getJsonValue1(response, "Month"));
		int year = Integer.parseInt((String)JsonUtil.getJsonValue1(response, "Year"));
		
		String recentDate = "";
		if(month < 9) {
			recentDate = String.valueOf(year).substring(2) + "0"+month;
		}else {
			recentDate = String.valueOf(year).substring(2) + month;
		}

		params.clear();
		params.put("CreditAcType", "0010");
		params.put("CurrencyFlag", "L");
		params.put("BillDate", recentDate);
		params.put("BillDay", billDay);
		// 请求3
		String response3 = SimpleHttpClient.post(
				"https://nper.cmbc.com.cn/pweb/CreditBillTitleQry.do", params,
				headers);
		logger.warn("------请求3------response：" + response3);
		String acNo = (String) JsonUtil.getJsonValue1(response3, "AcNo");

		List<String> payRecordList = new ArrayList<String>(16);
	    Map<String, String> bankListMap = new HashMap<>(16);
	    List<List<String>> dateList = new ArrayList<List<String>>(16);
	    List<String> bankList = new ArrayList<String>(16);
	    Map<String,Object> yueMap=new HashMap<>(16);
	    
		int total = 6;
		for (int j = 0; j < total; j++) {
			
			bankListMap.clear();
			payRecordList.clear();

			List<String> item = new ArrayList<String>(16);

			params.clear();
			params.put("CreditAcType", "0010");
			params.put("CurrencyFlag", "L");
			params.put("BillDate", recentDate);
			params.put("billDay", billDay);
			params.put("uri", "/pweb/CreditBillQry.do");
			params.put("currentIndex", 0);
			params.put("BillFlag", "1");
			params.put("AcNo", acNo);
			// 请求4
			response = SimpleHttpClient.post(
					"https://nper.cmbc.com.cn/pweb/CreditBillQry.do", params,
					headers);
			logger.warn("------请求4------response：" + response);
			
			payRecordList.add(response);
			
			if (response.equals("")) {
				break;
			}
			item.add(response);
			int pageNumber = (int) JsonUtil.getJsonValue1(response,
					"pageNumber");
			int begin = 2;
			if (pageNumber > 1) {
				int pageSize = (int) JsonUtil.getJsonValue1(response,
						"pageSize");
				int recordNumber = (int) JsonUtil.getJsonValue1(response,
						"recordNumber");
				params.put("recordNumber", recordNumber);

				for (int i = begin; i <= pageNumber; i++) {
					params.put("currentIndex", (i - 1) * pageSize);
					params.put("pageNo", i);
					// 请求5
					response = SimpleHttpClient.post(
							"https://nper.cmbc.com.cn/pweb/CreditBillQry.do",
							params, headers);
					logger.warn("------请求5------response：" + response);
					
					payRecordList.add(response);
					
					item.add(response);
				}
			}
			list.add(item);
			recentDate = this.lastDate(recentDate);
			//----数据解析 按月-----
		    yueMap.clear();
		    bankList.clear();
		    yueMap.put("response3", response3);
		    yueMap.put("payRecordlist", payRecordList);
		    bankList.add(yueMap.toString());
		    bankListMap.put("banklist", bankList.toString());
		    list = CmbcCreditAnalysis.getInfos(bankListMap,CreTLmit);
		    dateList.addAll(list);
		    //----数据解析-----
		}
		return dateList;
	}

	/**
	 * 获取上个月
	 * 
	 * @param date
	 * @return
	 * @throws ParseException
	 */
	public String lastDate(String date) throws ParseException {

		SimpleDateFormat format = new SimpleDateFormat("yyyyMM");
		// date格式为yyMM，在前面加上20才能变成完整的年月
		String currentDate = "20" + date;
		Date d = format.parse(currentDate);
		Calendar c = Calendar.getInstance();
		c.setTime(d);
		c.add(Calendar.MONTH, -1);
		String time = format.format(c.getTime());
		return time.substring(2);
	}

	
}