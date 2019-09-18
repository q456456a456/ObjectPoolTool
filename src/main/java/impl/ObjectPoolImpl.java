package impl;

import api.ObjectFactory;
import api.ObjectPool;
import api.PooledObject;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * 用于保存一种给定类型的对象的对象池。
 */
public class ObjectPoolImpl<T> implements ObjectPool<T> {
    Logger logger = Logger.getLogger(ObjectPoolImpl.class);
    /**
     * 获取对象时的最长等待时间，单位毫秒，默认为-1，表示一直等待
     */
    private volatile long maxWaitTime = -1L;
    /**
     * 空闲对象保持时间，达到此时间后空闲对象将会被移除，但会保留minFree个空闲对象，默认为-1表示一直不移除
     */
    private volatile long destoryTime = -1L;
    /**
     * 借出对象保持时间，达到此时间后借出对象仍未归还将会被移除，默认为-1表示一直不移除
     */
    private volatile long borrowTimeout = -1L;
    /**
     * 执行空闲对象移除方法的时间间隔，默认为-1表示一直不执行
     */
    private volatile long timeBetweenEviction = -1L;
    /**
     * 显示对象池是否已关闭的标志
     */
    private volatile boolean closed = false;
    /**
     * 表示对象池最大空闲数量
     */
    private volatile int maxFree = 8;
    /**
     * 表示对象池最小空闲数量
     */
    private volatile int minFree = 2;
    /**
     * 表示对象池最大对象数量
     */
    private volatile int maxTotal = 10;

    /**
     * 已创建对象总数（不包含已销毁的）
     */
    private final AtomicLong createCount;
    /**
     * 调用创建方法总线程数
     */
    private long makeObjectCount;
    /**
     * 创建对象时用的锁
     */
    private final Object makeObjectCountLock;
    /**
     * 已创建对象总数（包含已销毁的）
     */
    final AtomicLong createdCount = new AtomicLong(0L);
    /**
     * 默认对象存储策略为lifo
     */
    private boolean lifo = true;
    /**
     * 用于包装对象以放入对象池的工厂
     */
    private final ObjectFactory<T> factory;
    /**
     * 用于存放对象池中所有对象的Map
     */
    private final Map<T, PooledObject<T>> allObjects;
    /**
     * 一个双端阻塞队列，用于存储空闲对象
     */
    private final LinkedBlockingDeque<PooledObject<T>> freeObjects;


    public ObjectPoolImpl(ObjectFactory<T> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("不合法的对象生产工厂！");
        } else {
            this.factory = factory;
        }
        this.allObjects = Maps.newConcurrentMap();
        this.freeObjects = new LinkedBlockingDeque<>();
        this.createCount = new AtomicLong(0L);
        this.makeObjectCount = 0L;
        this.makeObjectCountLock = new Object();
    }

    public ObjectPoolImpl(ObjectFactory<T> factory, long maxWaitTime, long destoryTime, int maxFree, int minFree, int maxTotal,long timeBetweenEviction) {
        this(factory);
        this.maxWaitTime = maxWaitTime;
        this.destoryTime = destoryTime;
        this.borrowTimeout = borrowTimeout;
        if(minFree>maxFree||minFree>maxTotal||maxFree>maxTotal)
            throw new IllegalArgumentException("不合法的对象池大小参数");
        this.maxFree = maxFree;
        this.minFree = minFree;
        this.maxTotal = maxTotal;
        this.timeBetweenEviction = timeBetweenEviction;
        //启动定时回收借出时间过长的对象的任务
        if(timeBetweenEviction>0) {
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        evict();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                }
            };
            timer.schedule(timerTask, 0, timeBetweenEviction);
        }
    }


    /** 
    * @Description: 调用者从对象池获取一个对象 
    * @Param: [] 
    * @return: T 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public T borrowObject() throws Exception {
        return borrowObject(this.getMaxWaitTime());
    }
    
    /** 
    * @Description: 调用者在timeWait的超时时间内从对象池获取一个对象
     * 获取的逻辑为：
     * 1、检查并回收已借出中闲置的对象
     * 2、从闲置队列中获取
     * 3、若闲置队列中无对象，则调用create函数，通过工厂产生新的对象
     * 4、若创建成功则返回，若不成功则在timeWait时间里阻塞从闲置队列中获取，超时后抛出异常
     * 5、在返回获取的对象前，需要激活这个对象（修改对象状态等）
     * 6、考虑存在多个线程同时调用的情况，需要保证线程安全 
    * @Param: [timeWait] 
    * @return: T 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public T borrowObject(long timeWait) throws Exception {
        if (this.closed)
            throw new IllegalStateException("对象池未打开或已关闭！");
        //当空闲对象只有1个且使用对象数超过可以允许的总对象数-3时，检查并回收已借出中闲置的对象
        if (freeObjects.size() < 2 && allObjects.size() - freeObjects.size() > maxTotal - 3)
            removeAbandoned();

        PooledObject<T> p = null;
        p = freeObjects.pollFirst();
        if (p == null) {
            p = create();
        }
        if (p == null) {
            //timeWait<0表示不设置超时时间,会一直阻塞
            if (timeWait < 0) {
                p = freeObjects.takeFirst();
            } else {
                p = freeObjects.pollFirst(timeWait, TimeUnit.MILLISECONDS);
            }
            if (p == null)
                throw new NoSuchElementException("获取对象超时！");
        }
        if (!p.use()) {
            p = null;
        }
        if (p == null) {
            throw new IllegalStateException("更改对象状态失败！");
        }
        //获取对象成功，激活对象
        try {
            factory.activateObject(p);
        } catch (Exception e) {
            destroy(p);
            p = null;
            throw new NoSuchElementException("激活对象失败！");
        }
        System.out.println(Thread.currentThread().getName()+"：获取对象成功！");
        return p.getObject();
    }
    
    /** 
    * @Description: 调用向对象池归还一个对象
     * 归还的逻辑为：
     * 1、判断归还的对象是否属于对象池
     * 2、判断归还的对象状态是否为USED
     * 3、通过工厂钝化对象（还原对象初始状态等）
     * 4、判断pool是否关闭或存放空闲对象的队列是否已满
     * 5、将对象放回空闲对象的队列中
     * 6、考虑存在多个线程同时调用的情况，需要保证线程安全
    * @Param: [obj] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void returnObject(T obj) throws Exception {
        PooledObject<T> p = this.allObjects.get(obj);
        //归还的对象不属于对象池
        if (p == null)
            throw new NoSuchElementException("归还的对象不属于这个对象池！");
        //判断归还的对象状态不为USED
        if (this.allObjects.get(obj).getState() != PooledObjectState.USED)
            throw new IllegalStateException("对象已归还或出现其他未知错误！");
        //钝化
        try {
            factory.passivateObject(p);
        } catch (Exception e) {
            this.destroy(p);
        }
        if (!p.giveBack()) {
            throw new IllegalStateException("对象已归还或出现其他未知错误！");
        }
        //判断pool是否关闭或存放空闲对象的队列是否已满
        if (this.closed || maxFree <= this.freeObjects.size()) {
            this.destroy(p);
        } else {
            //LIFO策略
            if (lifo)
                this.freeObjects.addFirst(p);
            else
                this.freeObjects.addLast(p);
            if (this.closed) {
                this.clear();
            }
            System.out.println(Thread.currentThread().getName()+"：归还对象！");
        }
    }

    /** 
    * @Description: 销毁一个对象 
    * @Param: [obj] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void destroyObject(T obj) throws Exception {
        PooledObject<T> p = this.allObjects.get(obj);
        if (p == null)
            throw new IllegalStateException("要销毁的对象不在对象池中！");
        synchronized (p) {
            this.destroy(p);
        }
    }

    /** 
    * @Description: 直接向对象池中添加一个对象 
    * @Param: [obj] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void addObject(T obj) throws Exception {
        if (this.closed)
            throw new IllegalStateException("对象池未开启或已关闭！");
        PooledObject<T> p = this.create();
        if (lifo)
            this.freeObjects.addFirst(p);
        else
            this.freeObjects.addLast(p);
    }

    /** 
    * @Description: 处理已借出但闲置过久的对象
     * 当检测到某个对象的借出时间已经超过borrowTimeout就将其从对象池中销毁（不影响正在使用的线程）
    * @Param: [] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void removeAbandoned() throws Exception{
        if (this.borrowTimeout < 0)
            return;
        ArrayList<PooledObject<T>> removeList = new ArrayList();
        Iterator it = this.allObjects.values().iterator();
        while (it.hasNext()) {
            PooledObject<T> pooledObject = (PooledObject) it.next();
            synchronized (pooledObject){
                if (pooledObject.getState()==PooledObjectState.USED&&System.currentTimeMillis()-pooledObject.getLastUseTime()>borrowTimeout){
                    pooledObject.destory();
                    removeList.add(pooledObject);
                }
            }
        }
        Iterator itr = removeList.iterator();
        while(itr.hasNext()) {
            PooledObject<T> pooledObject = (PooledObject) itr.next();
            this.destroy(pooledObject);
        }
    }

    /** 
    * @Description: 处理闲置队列中闲置过久的对象 
     * 如果闲置队列中某个对象闲置时间超过destoryTime则将其销毁，同时会保证在销毁时闲置队列中的对象数量不少于minFree
    * @Param: [] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void evict() throws Exception{
        if(destoryTime<0)
            return;
        if (this.closed)
            throw new IllegalStateException("对象池未打开或已关闭！");
        if(freeObjects.size()>0){   //闲置队列中有对象
            boolean ifEvict = true;   //标记队尾对象是否被回收，若队尾对象都没有被回收，其他的对象不用再看
            while(ifEvict && freeObjects.size()>0){
                ifEvict = false;
                PooledObject<T> objectToEvict= freeObjects.peekLast();
                synchronized (objectToEvict){
                    if(objectToEvict.getState() == PooledObjectState.FREE &&    //确保当前对象仍为空闲
                            freeObjects.size()>this.getMinFree() &&            //当前空闲对象数量大于最小空闲数量
                            this.destoryTime<System.currentTimeMillis()-objectToEvict.getLastReturnTime() ){   //当前对象空闲时间大于设定值
                        objectToEvict.destory();
                        ifEvict = true;
                    }
                }
                if(ifEvict){
                    this.destroy(objectToEvict);
                }
            }
        }
        removeAbandoned();
    }

    /** 
    * @Description:  销毁创建的对象
    * @Param: [p] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void destroy(PooledObject<T> p) throws Exception {
        p.destory();
        this.freeObjects.remove(p);
        this.allObjects.remove(p.getObject());
        factory.destroyObject(p);
    }

    /** 
    * @Description: 创建新的对象
    * @Param: [] 
    * @return: api.PooledObject<T> 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public PooledObject<T> create() throws Exception {
        int localMaxTotal = this.getMaxTotal();   //设置对象池大小，若为负则设为最大整数
        if (localMaxTotal < 0) {
            localMaxTotal = Integer.MAX_VALUE;
        }
        long localStartTimeMillis = System.currentTimeMillis();   //开始时间
        long localMaxWaitTimeMillis = Math.max(this.getMaxWaitTime(), 0L);   //MaxWaitTime小于等于0表示无限期等待
        Boolean createFlag = null;    //是否可以获取对象

        //以下判断当前线程是否可以创建对象
        while (createFlag == null) {
            synchronized (this.makeObjectCountLock) {
                long newCreateCount = this.createCount.incrementAndGet();
                if (newCreateCount > (long) localMaxTotal) {  //如果这次创建之后超过对象池上限
                    this.createCount.decrementAndGet();
                    if (this.makeObjectCount == 0L) {    // 无其他线程正在调用makeObject()方法，意味着没有机会再创建对象，只能等待其他对象被归还
                        createFlag = Boolean.FALSE;     // 跳出循环
                        System.out.println(Thread.currentThread().getName()+"：对象池确认已满，创建失败");
                    } else {   //有其他线程在makeObject()，若它们创建失败，当前线程有机会再次创建，因此先等待
                        this.makeObjectCountLock.wait(localMaxWaitTimeMillis);
                        System.out.println(Thread.currentThread().getName()+"：对象池暂时满，等待");
                    }
                } else {              //当前未达到上限
                    ++this.makeObjectCount;
                    createFlag = Boolean.TRUE;
                    System.out.println(Thread.currentThread().getName()+"：对象池未满，可创建");
                }
            }
            //如果当前线程不是无限期等待，且等待超时
            if (createFlag == null && localMaxWaitTimeMillis > 0L && System.currentTimeMillis() - localStartTimeMillis >= localMaxWaitTimeMillis) {
                createFlag = Boolean.FALSE;
                System.out.println(Thread.currentThread().getName()+"：等待超时，创建失败");
            }
        }

        if (!createFlag) {  //不可创建对象
            return null;
        } else {           //可创建对象
                PooledObject<T> p;
                try {
                    p = this.factory.createObject();
                } catch (Throwable e) {
                    this.createCount.decrementAndGet();
                    System.out.println(Thread.currentThread().getName()+"：创建异常");
                    throw e;
                } finally {
                    //当前线程创建结束，唤醒其他等待线程
                        synchronized(this.makeObjectCountLock) {
                            --this.makeObjectCount;
                            this.makeObjectCountLock.notifyAll();
                            System.out.println(Thread.currentThread().getName()+"：当前线程创建结束");
                        }
                }
                System.out.println(Thread.currentThread().getName()+"：创建成功");
                this.createdCount.incrementAndGet();    //将创建总数增加
                this.allObjects.put(p.getObject(), p);   //将对象放入allObjects
                return p;
            }
        }

        /** 
        * @Description: 关闭对象池 
        * @Param: [] 
        * @return: void 
        * @Author: 薛谌
        * @Date: 2019/9/18 
        */
    public void close() {
        closed = true;
    }

    /** 
    * @Description: 清空闲置队列
    * @Param: [] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/18 
    */
    public void clear() throws Exception {
        for (PooledObject p = (PooledObject) this.freeObjects.poll(); p != null; p = (PooledObject) this.freeObjects.poll()) {
            this.destroy(p);
        }

    }

    public long getMaxWaitTime() {
        return maxWaitTime;
    }

    public void setMaxWaitTime(long maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    public long getDestoryTime() {
        return destoryTime;
    }

    public void setDestoryTime(long destoryTime) {
        this.destoryTime = destoryTime;
    }

    public long getTimeBetweenEviction() {
        return timeBetweenEviction;
    }

    private TimerTask timerTask = null;
    private Timer timer = new Timer();
    public void setTimeBetweenEviction(long timeBetweenEviction) {
        this.timeBetweenEviction = timeBetweenEviction;
        if(timeBetweenEviction<=0)
            return;
        if(timerTask!=null){
            timerTask.cancel();
            timerTask = null;
        }
        timer.purge();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    evict();
                }catch (Exception e){
                    System.out.println(e.getMessage());
                }
            }
        };
        timer.schedule(timerTask,0,timeBetweenEviction);
    }

    public int getMaxFree() {
        return maxFree;
    }

    public void setMaxFree(int maxFree) {
        if(maxFree<minFree||maxTotal<maxFree){
            logger.error("错误的maxTotal参数："+maxTotal);
            return;
        }
        this.maxFree = maxFree;
    }

    public int getMinFree() {
        return minFree;
    }

    public void setMinFree(int minFree) {
        if(maxFree<minFree){
            logger.error("错误的minFree参数："+minFree);
            return;
        }
        this.minFree = minFree;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        if(maxTotal<maxFree){
            logger.error("错误的maxTotal参数："+maxTotal);
            return;
        }
        this.maxTotal = maxTotal;
    }

}
