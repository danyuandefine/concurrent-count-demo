/**  
* Title NotSafeCountController.java  
* Description  线程不安全的计数统计示例
* @author danyuan
* @date Mar 8, 2020
* @version 1.0.0
* site: www.danyuanblog.com
*/ 
package com.danyuanblog.test.concurrent.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotSafeCountController {
	private Integer count = 0;
	
	/**
	 * 查询统计结果
	 * @author danyuan
	 */
	@GetMapping("/showResult")
	public Integer showResult(){
		return count;
	}
	
	/**
	 * 访问一次增加一次统计计数
	 * @author danyuan
	 */
	@GetMapping("/addCount")
	public void addCount(){
		count++;
	}
}
