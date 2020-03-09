/**  
* Title LockCountController.java  
* Description  利用ReentrantLock同步锁保证线程安全的计数统计示例
* @author danyuan
* @date Mar 8, 2020
* @version 1.0.0
* site: www.danyuanblog.com
*/ 
package com.danyuanblog.test.concurrent.test;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LockCountController {
	private Integer count = 0;
	private Lock lock = new ReentrantLock();
	
	/**
	 * 查询统计结果
	 * @author danyuan
	 */
	@GetMapping("/lock/showResult")
	public Integer showResult(){
		return count;
	}
	
	/**
	 * 访问一次增加一次统计计数
	 * @author danyuan
	 */
	@GetMapping("/lock/addCount")
	public void addCount(){
		lock.lock();
		try {//使用加锁的方式保证请求串行计数
			count++;
		}finally{
			lock.unlock();
		}	
	}
}
