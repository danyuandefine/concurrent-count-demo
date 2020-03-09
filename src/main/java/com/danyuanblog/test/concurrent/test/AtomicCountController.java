/**  
* Title AtomicCountController.java  
* Description  利用AtomicInteger保证线程安全的计数统计示例
* @author danyuan
* @date Mar 8, 2020
* @version 1.0.0
* site: www.danyuanblog.com
*/ 
package com.danyuanblog.test.concurrent.test;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AtomicCountController {
	private AtomicInteger count = new AtomicInteger(0);
	
	/**
	 * 查询统计结果
	 * @author danyuan
	 */
	@GetMapping("/atomic/showResult")
	public Integer showResult(){
		return count.get();
	}
	
	/**
	 * 访问一次增加一次统计计数
	 * @author danyuan
	 */
	@GetMapping("/atomic/addCount")
	public void addCount(){//利用AtomicInteger的方法来保证原子性
		count.incrementAndGet();
	}
}
