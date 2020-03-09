####  简介
> * 最近复习了一下JMM的相关内容，总结了下如何处理多线程间访问共享变量带来的数据不一致问题的解决方案及原理说明，主要使用到的方法有synchronized`关键字、`volatile`关键字、`ReentrantLock`同步锁、`java.util.concurrent.atomic`工具包、`ThreadLocal`等等。
> > 本文将介绍如何分析并解决JAVA多线程并发访问线程间共享变量导致的数据不一致问题，内容有
> > * JMM内存模型的简介
> > * JMM内存模型带来的问题
> > * 原子性、可见性、指令重排序的简单介绍
> > * 存在并发问题的代码示例和测试
> > * 解决方案及源码示例

***
#### 1、JMM内存模型的由来

> * 计算机内存模型
> >
> > 计算机在完成一些任务时，通常处理器要与内存进行交互，如读取运算数据、存储运算结果等，这个I/O操作是很难消除的（无法仅靠寄存器来完成所有运算任务）。早期计算机中cpu和内存的速度是差不多的，但在现代计算机中，cpu的指令速度远超内存的存取速度,由于计算机的存储设备与处理器的运算速度有几个数量级的差距，所以现代计算机系统都不得不加入一层读写速度尽可能接近处理器运算速度的高速缓存（Cache）来作为内存与处理器之间的缓冲：将运算需要使用到的数据复制到缓存中，让运算能快速进行，当运算结束后再从缓存同步回内存之中，这样处理器就无须等待缓慢的内存读写了。
>
> ![计算机CPU和缓存架构](http://www.danyuanblog.com/file-gateway//ueditor/jsp/upload/image/20200308/1583681037731082519.png)
>
> * JVM内存模型
>
> > JMM定义了Java 虚拟机(JVM)在计算机内存(RAM)中的工作方式。JVM是整个计算机虚拟模型，所以JMM是隶属于JVM的。从抽象的角度来看，JMM定义了线程和主内存之间的抽象关系：线程之间的共享变量存储在主内存（Main Memory）中，每个线程都有一个私有的本地内存（Local Memory），本地内存中存储了该线程以读/写共享变量的副本。本地内存是JMM的一个抽象概念，并不真实存在。它涵盖了缓存、写缓冲区、寄存器以及其他的硬件和编译器优化。
>
> ![JVM内存模型](http://www.danyuanblog.com/file-gateway//ueditor/jsp/upload/image/20200308/1583681436752082992.png)
>
> 

#### 2、JMM内存模型带来的问题

> ​    前提条件，JMM内存模型在单线程环境下不会存在什么问题，主要描述的是线程间对同一共享变量进行读写操作的时候，由于线程间可见性和竞争关系导致的数据一致性问题。    
>
> ​    举个栗子，对于共享变量count初始值为0，每个线程都在自己的工作内存中存储了一份副本，每次对该变量的读写都是先对工作内存中的副本进行操作，然后再同步主内存里的变量；这里有两个线程A、B，他们同时对该线程进行频繁的访问，他们每访问一次，count就加一，用来记录A、B线程总共的访问次数。  
>
>    当线程A、B都将变量count=0读入工作内存，此时线程A会对工作内存的count++后设置count=1,这个时候count=1的值还未写入主内存，而且线程B也和A做了类似的操作得到count=1，这是A、B线程依次将count=1同步回主内存，得到最终结果为count=1,这样就导致了访问次数统计不准的现象。
>
> * 可见性问题  
>
> ​     指的是线程间对其他线程的工作内存里的变量值是不可见的，导致该线程读取到的共享变量count的值可能不是最新的。
>
> * 多线程间读写竞争问题  
>
>     由于线程A和线程B都需要对共享变量count进行修改，存在写入到主内存的值会存在覆盖的可能。

#### 3、原子性、可见性、指令重排序的简单介绍

> * 原子性
>
>     原子性是指**一个操作是不可中断的，要么全部执行成功要么全部执行失败，有着“同生共死”的感觉**。及时在多个线程一起执行的时候，一个操作一旦开始，就不会被其他线程所干扰。
>
> * 线程可见性
>
>     可见性是指当一个线程修改了共享变量后，其他线程能够立即得知这个修改
>
> * 指令重排序及有序性
>
>     ​    代码指令的执行顺序对程序的结果有着很大的影响，特别是前后指令之间有依赖关系。但是我们的程序指令在转化为计算机能直接执行的命令前，会经过很多优化，包括指令的编码以及指令间的顺序等等，其目的是为了让程序更高效的执行。但是有的时候也有一定的副作用，我们需要再编程的时候避开这些坑。  
>
>     ​    指令重排序大致上有如下几个阶段会对代码进行优化，也就是说我们写的代码的顺序可能会发生一些变化。
>
>     ​    ![指令重排序](http://www.danyuanblog.com/file-gateway//ueditor/jsp/upload/image/20200309/1583686038037077014.png)
>
>     > java语言提供了很多工具和方法来帮助我们解决JMM带了的副作用，如：`synchronized`关键字、`volatile`关键字、`ReentrantLock`同步锁、`java.util.concurrent.atomic`工具包、`ThreadLocal`等等。

#### 4、存在并发问题的代码示例和测试

> 本文将介绍一个常见的统计接口访问次数的功能实现，并通过实验分析其结果。
>
> * 代码如下
>
> ```java
> /**  
> * Title NotSafeCountController.java  
> * Description  线程不安全的计数统计示例
> * @author danyuan
> * @date Mar 8, 2020
> * @version 1.0.0
> * site: www.danyuanblog.com
> */ 
> package com.danyuanblog.test.concurrent.test;
> 
> import org.springframework.web.bind.annotation.GetMapping;
> import org.springframework.web.bind.annotation.RestController;
> 
> @RestController
> public class NotSafeCountController {
> 	private Integer count = 0;
> 	
> 	/**
> 	 * 查询统计结果
> 	 * @author danyuan
> 	 */
> 	@GetMapping("/showResult")
> 	public Integer showResult(){
> 		return count;
> 	}
> 	
> 	/**
> 	 * 访问一次增加一次统计计数
> 	 * @author danyuan
> 	 */
> 	@GetMapping("/addCount")
> 	public void addCount(){
> 		count++;
> 	}
> }
> ```
>
> * 进行访问测试
>
>     ```shell
>     ab -n 10000 -c 200 192.168.1.8:8080/addCount #同时使用200线程执行10000次访问
>     ```
>
>     得到结果如下：
>
>     ```ruby
>     Percentage of the requests served within a certain time (ms)#执行时间如下
>       50%    543
>       66%    587
>       75%    617
>       80%    645
>       90%    829
>       95%    989
>       98%   1068
>       99%   1091
>      100%   1203 (longest request)
>     [root@10 ~]# curl 192.168.1.8:8080/showResult #总次数
>     9572
>     ```
>
>     可以看到结果并不是我么预期的10000，看似很正常的一段代码，业务逻辑非常简单明了，但是却得到了以外的结果，惊不惊喜！
>
>     看过前面的铺垫后，看到这个结果也许并不意外，其实这是一个线程间不可见和非原子性导致的这个问题，感兴趣的同学可以思考一下为什么。

#### 5、解决方案及源码示例

> 上面提到的这里是由于线程间对各自工作内存中的共享变量的不可见性和非原子性问题导致的，具体是为啥呢？
>
> 线程间共享变量副本的不可见性，大家应该都明白了，那非原子性又怎么说呢，我们可以回忆一下上面讲到的原子性，也就是一个操作是不可中断的，要么全部执行成功要么全部执行失败，这里的count++就有问题了，我们可以把它拆分一下：
>
> ```ruby
>  1. 读取变量count的值；
>  2. 对count进行加一的操作；
>  3. 将计算后的值再赋值给变量count
> ```
>
> 也就是说，不同的线程执行这个操作都需要执行这三个步骤，而且都是可以被打断的，这样一来也有可能会导致count变量值得不一致。
>
> 如何解决这两个问题呢？下面我们来介绍一下java提供的工具和用法。
>
> * 1、`volatile`关键字
>
>     > 保证读写的都是主内存的变量，且不会对该关键字修饰的变量进行指令重排序，可以保证可见性和有序性。
>
>     * 利用volatile改造上面的逻辑
>
>  ```java
>  /**  
>     * Title VolatileCountController.java  
>     * Description  利用volatile关键字保证线程安全的计数统计示例
>     * @author danyuan
>     * @date Mar 8, 2020
>     * @version 1.0.0
>     * site: www.danyuanblog.com
>  */ 
>  package com.danyuanblog.test.concurrent.test;
>  
>  import org.springframework.web.bind.annotation.GetMapping;
>  import org.springframework.web.bind.annotation.RestController;
>  
>  @RestController
>  public class VolatileCountController {
>  	private volatile Integer count = 0;
>  	
>  	/**
>     	 * 查询统计结果
>     	 * @author danyuan
>  	 */
>  	@GetMapping("/volatile/showResult")
>  	public Integer showResult(){
>  		return count;
>  	}
>  	
>  	/**
>     	 * 访问一次增加一次统计计数
>     	 * @author danyuan
>  	 */
>  	@GetMapping("/volatile/addCount")
>  	public void addCount(){
>  			count++;
>  	}
>  }
>  ```
>
>     * 进行压测
>
>  ```shell
>  ab -n 10000 -c 200 192.168.1.8:8080/volatile/addCount
>  ```
>
>     * 结果
>
>  ```ruby
>  Percentage of the requests served within a certain time (ms)
>    50%    530
>    66%    592
>    75%    649
>    80%    700
>    90%    813
>    95%    898
>    98%    964
>    99%   1012
>   100%   1444 (longest request)
>  [root@10 ~]# curl 192.168.1.8:8080/volatile/showResult
>  9542
>  ```
>
>     > 显然结果还是错误的，虽然volatile满足了可见性和有序性，但是无法保证指令逻辑的原子性，所以不能得到我们预期的结果
>
> * 2、`synchronized`关键字
>
>     > ​	synchronized经过编译后，会在同步块前后分别形成monitorenter和monitorexit两个字节码指令，在执行monitorenter指令时，首先要尝试获取对象锁，如果对象没有别锁定，或者当前已经拥有这个对象锁，把锁的计数器加1，相应的在执行monitorexit指令时，会将计数器减1，当计数器为0时，锁就被释放了。如果获取锁失败，那当前线程就要阻塞，直到对象锁被另一个线程释放为止。
>  >
>     > ​	synchronized可以保证可见性、有序性和代码块的原子性，原则上它是一种对象锁，只要某线程获取到该对象锁后，其他使用该对象锁的同步代码块均进入阻塞状态，等待该线程执行完释放该对象锁后，其他线程才能One by One 的进行执行，在高并发的场景，其处理效率是非常低效的。
>  >
>     > * 利用synchronized改造上面的代码
>  >
>     >     ```java
>     >     /**  
>     >     * Title SynchronizedCountController.java  
>     >     * Description  利用synchronized对象访问同步锁保证线程安全的计数统计示例
>     >     * @author danyuan
>     >     * @date Mar 8, 2020
>     >     * @version 1.0.0
>     >     * site: www.danyuanblog.com
>     >     */ 
>     >     package com.danyuanblog.test.concurrent.test;
>     >     
>     >     import org.springframework.web.bind.annotation.GetMapping;
>     >     import org.springframework.web.bind.annotation.RestController;
>     >     
>     >     @RestController
>     >     public class SynchronizedCountController {
>     >     	private Integer count = 0;
>     >     	
>     >     	/**
>     >     	 * 查询统计结果
>     >     	 * @author danyuan
>     >     	 */
>     >     	@GetMapping("/sync/showResult")
>     >     	public Integer showResult(){
>     >     		return count;
>     >     	}
>     >     	
>     >     	/**
>     >     	 * 访问一次增加一次统计计数
>     >     	 * @author danyuan
>     >     	 */
>     >     	@GetMapping("/sync/addCount")
>     >     	public void addCount(){
>     >     		synchronized (this) {//使用加锁的方式保证请求串行计数
>     >     			count++;
>     >     		}		
>     >     	}
>     >     }
>     >     ```
>  >
>     > * 进行压测
>  >
>     >     ```shell
>     >     ab -n 10000 -c 200 192.168.1.8:8080/sync/addCount
>     >     ```
>  >
>     > * 结果
>  >
>     >     ```ruby
>     >     Percentage of the requests served within a certain time (ms)
>     >       50%    453
>     >       66%    538
>     >       75%    616
>     >       80%    684
>     >       90%    898
>     >       95%   1190
>     >       98%   1705
>     >       99%   2050
>     >      100%   2162 (longest request)
>     >     [root@10 ~]# curl 192.168.1.8:8080/sync/showResult
>     >     10000
>     >     ```
>  >
>     >     可以看到访问结果统计正确了，但是执行时间几乎延长了一倍左右。
>
> * 3、`ReentrantLock`同步锁
>
>     > 由于ReentrantLock是java.util.concurrent包下面提供的一套互斥锁，简单来说，ReenTrantLock的实现是一种自旋锁，通过循环调用CAS操作来实现加锁。它的性能比较好也是因为避免了使线程进入内核态的阻塞状态。相比Synchronized类提供了一些高级的功能，主要有一下三项：
>  >
>     > **3.1 等待可中断**，持有锁的线程长期不释放的时候，正在等待的线程可以选择放弃等待，这相当于Synchronized来说可以避免出现死锁的情况。通过lock.lockInterruptibly()来实现这个机制。
>  >
>     > **3.2 公平锁**，多个线程等待同一个锁时，必须按照申请锁的时间顺序获得锁，Synchronized锁非公平锁，ReentrantLock默认的构造函数是创建的非公平锁，可以通过参数true设为公平锁，但公平锁表现的性能不是很好。
>     >  *公平锁、非公平锁的创建方式：*
>  >
>     > ```csharp
>     > //创建一个非公平锁，默认是非公平锁
>     > Lock lock = new ReentrantLock();
>     > Lock lock = new ReentrantLock(false);
>     >  
>     > //创建一个公平锁，构造传参true
>     > Lock lock = new ReentrantLock(true);
>     > ```
>  >
>     > **3.3 锁绑定多个条件**，一个ReentrantLock对象可以同时绑定对个对象。ReenTrantLock提供了一个Condition（条件）类，用来实现分组唤醒需要唤醒的线程们，而不是像synchronized要么随机唤醒一个线程要么唤醒全部线程。
>  >
>     > * 利用synchronized改造上面的代码
>  >
>     >     ```java
>     >     /**  
>     >     * Title LockCountController.java  
>     >     * Description  利用ReentrantLock同步锁保证线程安全的计数统计示例
>     >     * @author danyuan
>     >     * @date Mar 8, 2020
>     >     * @version 1.0.0
>     >     * site: www.danyuanblog.com
>     >     */ 
>     >     package com.danyuanblog.test.concurrent.test;
>     >     
>     >     import java.util.concurrent.locks.Lock;
>     >     import java.util.concurrent.locks.ReentrantLock;
>     >     
>     >     import org.springframework.web.bind.annotation.GetMapping;
>     >     import org.springframework.web.bind.annotation.RestController;
>     >     
>     >     @RestController
>     >     public class LockCountController {
>     >     	private Integer count = 0;
>     >     	private Lock lock = new ReentrantLock();
>     >     	
>     >     	/**
>     >     	 * 查询统计结果
>     >     	 * @author danyuan
>     >     	 */
>     >     	@GetMapping("/lock/showResult")
>     >     	public Integer showResult(){
>     >     		return count;
>     >     	}
>     >     	
>     >     	/**
>     >     	 * 访问一次增加一次统计计数
>     >     	 * @author danyuan
>     >     	 */
>     >     	@GetMapping("/lock/addCount")
>     >     	public void addCount(){
>     >     		lock.lock();
>     >     		try {//使用加锁的方式保证请求串行计数
>     >     			count++;
>     >     		}finally{
>     >     			lock.unlock();
>     >     		}	
>     >     	}
>     >     }
>     >     ```
>  >
>     > * 进行压测
>  >
>     >     ```shell
>     >     ab -n 10000 -c 200 192.168.1.8:8080/lock/addCount
>     >     ```
>  >
>     > * 结果
>  >
>     >     ```ruby
>     >     Percentage of the requests served within a certain time (ms)
>     >       50%    455
>     >       66%    529
>     >       75%    567
>     >       80%    606
>     >       90%    771
>     >       95%    850
>     >       98%   1086
>     >       99%   1143
>     >      100%   1563 (longest request)
>     >     [root@10 ~]# curl 192.168.1.8:8080/lock/showResult
>     >     10000
>     >     ```
>  >
>     >     可以看到，我们同样得到了正确的结果，而且性能还比synchronized方式高很多。
>
> * 4、java.util.concurrent.atomic`工具包
>
>     > 这个就很简单了，前面是我们自己写代码保证我们代码的可见性、有序性和原子性，这个工具包下的工具类直接给我们封装好了这些功能，开箱即用。
>  >
>     > * 改造代码如下：
>  >
>     >     ```java
>     >     /**  
>     >     * Title AtomicCountController.java  
>     >     * Description  利用AtomicInteger保证线程安全的计数统计示例
>     >     * @author danyuan
>     >     * @date Mar 8, 2020
>     >     * @version 1.0.0
>     >     * site: www.danyuanblog.com
>     >     */ 
>     >     package com.danyuanblog.test.concurrent.test;
>     >     
>     >     import java.util.concurrent.atomic.AtomicInteger;
>     >     
>     >     import org.springframework.web.bind.annotation.GetMapping;
>     >     import org.springframework.web.bind.annotation.RestController;
>     >     
>     >     @RestController
>     >     public class AtomicCountController {
>     >     	private AtomicInteger count = new AtomicInteger(0);
>     >     	
>     >     	/**
>     >     	 * 查询统计结果
>     >     	 * @author danyuan
>     >     	 */
>     >     	@GetMapping("/atomic/showResult")
>     >     	public Integer showResult(){
>     >     		return count.get();
>     >     	}
>     >     	
>     >     	/**
>     >     	 * 访问一次增加一次统计计数
>     >     	 * @author danyuan
>     >     	 */
>     >     	@GetMapping("/atomic/addCount")
>     >     	public void addCount(){//利用AtomicInteger的方法来保证原子性
>     >     		count.incrementAndGet();
>     >     	}
>     >     }
>     >     ```
>  >
>     > * 进行压测
>  >
>     >     ```shell
>     >     ab -n 10000 -c 200 192.168.1.8:8080/atomic/addCount
>     >     ```
>  >
>     > * 结果
>  >
>     >     ```ruby
>     >     Percentage of the requests served within a certain time (ms)
>     >       50%    448
>     >       66%    511
>     >       75%    559
>     >       80%    592
>     >       90%    711
>     >       95%   1028
>     >       98%   1768
>     >       99%   1930
>     >      100%   2000 (longest request)
>     >     [root@10 ~]# curl 192.168.1.8:8080/atomic/showResult
>     >     10000
>     >     ```
>  >
>     >     显然也达到了我们预期的结果，但是性能也不是很理想。
>
> * 5、`ThreadLocal`工具类
>
>     > ThreadLocal提供了线程内存储变量的能力，这些变量不同之处在于每一个线程读取的变量是对应的互相独立的。通过get和set方法就可以得到当前线程对应的值。其实使用ThreadLocale就是说，我们以前不是有线程间访问共享变量有问题吗，那简单，我们各线程自己做自己的操作，互不影响就行了，最后把所有结果加起来不就是总结果了吗，完全避免了线程同步的问题，这里还使用到了分布式计算的思想Map->Reduce。
>  >
>  >* 代码如下：
>  >
>  >```java
>  >/**  
>  >    * Title ThreadLocalCountController.java  
>  >    * Description  使用ThreadLocal保证多线程安全的计数统计示例
>  >    * @author danyuan
>  >    * @date Mar 8, 2020
>  >    * @version 1.0.0
>  >    * site: www.danyuanblog.com
>  >*/ 
>  >package com.danyuanblog.test.concurrent.test;
>  >
>  >import java.util.HashSet;
>  >
>  >import org.springframework.web.bind.annotation.GetMapping;
>  >import org.springframework.web.bind.annotation.RestController;
>  >
>  >@RestController
>  >public class ThreadLocalCountController {
>  >	private static HashSet<Counter> set = new HashSet<>();
>  >	private ThreadLocal<Counter> count = new ThreadLocal<Counter>(){
>  >		@Override
>  >		protected Counter initialValue() {
>  >			Counter counter = new Counter();
>  >			synchronized (set) {
>  >				set.add(counter);//由于这段代码不是线程安全的，所以需要加锁
>  >			}
>  >			counter.setCount(0);
>  >			return counter;
>  >		};
>  >	};
>  >	
>  >	class Counter{
>  >		private Integer count;
>  >
>  >		/**
>  >    		 * @return the count
>  >		 */
>  >		public Integer getCount() {
>  >			return count;
>  >		}
>  >
>  >		/**
>  >    		 * @param count the count to set
>  >		 */
>  >		public void setCount(Integer count) {
>  >			this.count = count;
>  >		}		
>  >	}
>  >	/**
>  >    	 * 查询统计结果
>  >    	 * @author danyuan
>  >	 */
>  >	@GetMapping("/threadLocal/showResult")
>  >	public Integer showResult(){
>  >		return set.stream().map(x -> x.getCount()).reduce(0, (a,b) ->  a + b);
>  >	}
>  >	
>  >	/**
>  >    	 * 访问一次增加一次统计计数
>  >    	 * @author danyuan
>  >	 */
>  >	@GetMapping("/threadLocal/addCount")
>  >	public void addCount(){//利用ThreadLocal实现分布式计算，每个线程自己统计自己的,避免上锁操作
>  >		Counter counter = count.get();
>  >		counter.setCount(counter.getCount()+1);
>  >	}
>  >}
>  >```
>  >
>  >* 进行压测
>  >
>  >```shell
>  >ab -n 10000 -c 200 192.168.1.8:8080/threadLocal/addCount
>  >```
>  >
>  >* 结果
>  >
>  >```ruby
>  >Percentage of the requests served within a certain time (ms)
>  >  50%    558
>  >  66%    601
>  >  75%    628
>  >  80%    657
>  >  90%    734
>  >  95%    798
>  >  98%    868
>  >  99%    887
>  > 100%   1118 (longest request)
>  >[root@10 ~]# curl 192.168.1.8:8080/threadLocal/showResult
>  >10000
>  >```
>  >
>  >可以看到我们同样获得了预期的结果，而且跟之前没有做线程安全处理耗费的时间相差无几，性能非常高，这也就是ThreadLocal的妙用之一。
>  >

***

**相关文章推荐**

[ab压测工具使用教程](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5e5d03c04c636312f4b70901)

[spring cloud config 配置中心使用与避坑指南](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5d6ea675d4c6510c1d63b308)

[springcloud微服务组件之feign的应用](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5d7b2705d4c6510c1d63b313)

[springcloudstream整合rabbitmq及其应用实战，点对点、发布订阅、消息分组与持久化等等](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5d83bdcbd4c6510c1d63b325)

[zuul微服务网关实战教程](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5d862b68d4c6510c1d63b32d)

[springboot应用制作docker镜像教程](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5dac0c9c4c636361c5e75e96)

[springadmin微服务监控与报警](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5dac7e4c4c636361c5e75e9a)

[ELK日志收集系统介绍及过期系统日志清理](http://www.danyuanblog.com/blog/app/blog/blogDetail.html?id=5e6337494c636312f4b70904)