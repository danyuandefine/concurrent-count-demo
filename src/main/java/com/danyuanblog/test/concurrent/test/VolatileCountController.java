/**  
* Title VolatileCountController.java  
* Description  利用volatile关键字保证线程安全的计数统计示例
* @author danyuan
* @date Mar 8, 2020
* @version 1.0.0
* site: www.danyuanblog.com
*/ 
package com.danyuanblog.test.concurrent.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VolatileCountController {
	private volatile Integer count = 0;
	
	/**
	 * 查询统计结果
	 * @author danyuan
	 */
	@GetMapping("/volatile/showResult")
	public Integer showResult(){
		return count;
	}
	
	/**
	 * 访问一次增加一次统计计数
	 * @author danyuan
	 */
	@GetMapping("/volatile/addCount")
	public void addCount(){
			count++;
	}
}
