/**  
* Title ThreadLocalCountController.java  
* Description  使用ThreadLocal保证多线程安全的计数统计示例
* @author danyuan
* @date Mar 8, 2020
* @version 1.0.0
* site: www.danyuanblog.com
*/ 
package com.danyuanblog.test.concurrent.test;

import java.util.HashSet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThreadLocalCountController {
	private static HashSet<Counter> set = new HashSet<>();
	private ThreadLocal<Counter> count = new ThreadLocal<Counter>(){
		@Override
		protected Counter initialValue() {
			Counter counter = new Counter();
			synchronized (set) {
				set.add(counter);
			}
			counter.setCount(0);
			return counter;
		};
	};
	
	class Counter{
		private Integer count;

		/**
		 * @return the count
		 */
		public Integer getCount() {
			return count;
		}

		/**
		 * @param count the count to set
		 */
		public void setCount(Integer count) {
			this.count = count;
		}		
	}
	/**
	 * 查询统计结果
	 * @author danyuan
	 */
	@GetMapping("/threadLocal/showResult")
	public Integer showResult(){
		return set.stream().map(x -> x.getCount()).reduce(0, (a,b) ->  a + b);
	}
	
	/**
	 * 访问一次增加一次统计计数
	 * @author danyuan
	 */
	@GetMapping("/threadLocal/addCount")
	public void addCount(){//利用ThreadLocal实现分布式计算，每个线程自己统计自己的,避免上锁操作
		Counter counter = count.get();
		counter.setCount(counter.getCount()+1);
	}
}
