/**  
* Title SynchronizedCountController.java  
* Description  利用synchronized对象访问同步锁保证线程安全的计数统计示例
* @author danyuan
* @date Mar 8, 2020
* @version 1.0.0
* site: www.danyuanblog.com
*/ 
package com.danyuanblog.test.concurrent.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SynchronizedCountController {
	private Integer count = 0;
	
	/**
	 * 查询统计结果
	 * @author danyuan
	 */
	@GetMapping("/sync/showResult")
	public Integer showResult(){
		return count;
	}
	
	/**
	 * 访问一次增加一次统计计数
	 * @author danyuan
	 */
	@GetMapping("/sync/addCount")
	public void addCount(){
		synchronized (this) {//使用加锁的方式保证请求串行计数
			count++;
		}		
	}
}
