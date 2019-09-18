package impl;

import api.ObjectFactory;
import api.ObjectPool;
import api.PooledObject;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用于保存一种给定类型的对象的对象池。
 */
public class ObjectPoolImpl<T> implements ObjectPool<T> {
    /** 获取对象时的最长等待时间，单位毫秒，默认为-1，表示一直等待*/
    private volatile long maxWaitTime = -1L;
    /** 空闲对象保持时间，达到此时间后空闲对象将会被移除，但会保留minFree个空闲对象，默认为-1表示一直不移除*/
    private volatile long destoryTime = -1L;
    /** 显示对象池是否已关闭的标志*/
    private volatile boolean closed = false;
    /** 表示对象池最大空闲数量*/
    private volatile int maxFree = 8;
    /** 表示对象池最小空闲数量*/
    private volatile int minFree = 2;
    /** 表示对象池最大对象数量*/
    private volatile int maxTotal = 8;
    /** 通过工厂已创建的所有对象的个数*/
    private final AtomicLong createCount;
//    /** 默认对象存储策略为lifo*/
//    private boolean lifo = true;
    /** 用于包装对象以放入对象池的工厂*/
    private final ObjectFactory<T> factory;
    /** 用于存放对象池中所有对象的Map*/
    private final Map<T, PooledObject<T>> allObjects;
    /** 一个双端阻塞队列，用于存储空闲对象*/
    private final LinkedBlockingDeque<PooledObject<T>> freeObjects;


    public ObjectPoolImpl(ObjectFactory<T> factory){
        if(factory==null){
            throw new IllegalArgumentException("不合法的对象生产工厂！");
        }else{
            this.factory = factory;
        }
        this.allObjects = Maps.newConcurrentMap();
        this.freeObjects = new LinkedBlockingDeque<>();
        this.createCount = new AtomicLong(0L);
    }
    //这里需要做异常处理，防止不合法的time或容量，或maxFree<minFree的情况，底下的set也需要
    public ObjectPoolImpl(ObjectFactory<T> factory,long maxWaitTime,long destoryTime,int maxFree,int minFree,int maxTotal){
        this(factory);
        this.maxWaitTime = maxWaitTime;
        this.destoryTime = destoryTime;
        this.maxFree = maxFree;
        this.minFree = minFree;
        this.maxTotal = maxTotal;
    }


    public T borrowObject() throws Exception {
        return borrowObject(this.getMaxWaitTime());
    }
    /**借出的逻辑为：
     * 1、检查并回收已借出中闲置的对象
     * 2、从闲置队列中获取
     * 3、若闲置队列中无对象，则调用create函数，通过工厂产生新的对象
     * 4、若创建成功则返回，若不成功则在timeWait时间里阻塞从闲置队列中获取，超时后抛出异常
     * 5、考虑存在多个线程同时调用的情况，需要保证线程安全
     */
    public T borrowObject(long timeWait) throws Exception {
        if(this.closed)
            throw new IllegalStateException("对象池未打开或已关闭！");
        //当空闲对象只有1个且使用对象数超过可以允许的总对象数-3时，检查并回收已借出中闲置的对象
        if(freeObjects.size()<2&&allObjects.size()-freeObjects.size()>maxTotal-3)
            removeAbandoned();

        PooledObject<T> p = null;
        p = freeObjects.pollFirst();
        if(p==null){
            p = create();
        }
        if(p==null) {
            //timeWait<0表示不设置超时时间,会一直阻塞
            if (timeWait < 0) {
                p = freeObjects.takeFirst();
            } else {
                p = freeObjects.pollFirst(timeWait, TimeUnit.MILLISECONDS);
            }
            if(p==null)
                throw new NoSuchElementException("获取对象超时！");
        }
        if(!p.use()){
            p=null;
        }
        return p.getObject();
    }

    public boolean returnObject(T obj) throws Exception {
        return false;
    }

    public boolean destroyObject(T obj) throws Exception {
        return false;
    }

    public boolean addObject(T obj) throws Exception {
        return false;
    }

    //处理已借出但闲置过久的对象
    public void removeAbandoned(){
    };
    //定时处理闲置队列中闲置过久的对象
    public void evict(){};
    //创建新的对象
    public PooledObject<T> create() throws Exception{

    }

    public void close() {
        closed = true;
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

    public int getMaxFree() {
        return maxFree;
    }

    public void setMaxFree(int maxFree) {
        this.maxFree = maxFree;
    }

    public int getMinFree() {
        return minFree;
    }

    public void setMinFree(int minFree) {
        this.minFree = minFree;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }
}
