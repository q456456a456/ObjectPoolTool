package impl;

import api.PooledObject;

public class PooledObjectImpl<T> implements PooledObject<T> {
    private final T object;                  //对象本身
    private PooledObjectState state;         //对象在对象池中的状态
    private final long createTime;         //对象创建的时间
    private volatile long lastBorrowTime; //对象上一次被借出使用的时间
    private volatile long lastReturnTime; //对象上一次归还的时间

    public PooledObjectImpl(T object){
        this.object = object;
        this.state = PooledObjectState.FREE;
        this.createTime =  System.currentTimeMillis();
        this.lastBorrowTime = createTime;
        this.lastReturnTime = createTime;
    }

    /**
    * @Description: 返回对象本身
    * @Param: []
    * @return: T
    * @Author: 薛谌
    * @Date: 2019/9/17
    */
    @Override
    public T getObject() {
        return this.object;
    }

    /**
    * @Description: 获取对象在对象池中的状态
    * @Param: []
    * @return: impl.PooledObjectState
    * @Author: 薛谌
    * @Date: 2019/9/17
    */
    @Override
    public PooledObjectState getState() {
        return this.state;
    }

    /**
    * @Description: 有某个线程从对象池中获取并使用该对象
    * @Param: []
    * @return: boolean
    * @Author: 薛谌
    * @Date: 2019/9/17
    */
    @Override
    public synchronized boolean use() {
        if(this.state == PooledObjectState.FREE){
            this.state = PooledObjectState.USED;
            this.lastBorrowTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /** 
    * @Description: 有某个线程将该对象归还至对象池 
    * @Param: [] 
    * @return: boolean 
    * @Author: 薛谌
    * @Date: 2019/9/17 
    */
    @Override
    public synchronized boolean giveBack() {
        if(this.state == PooledObjectState.USED||this.state == PooledObjectState.RETURNING){
            this.state = PooledObjectState.FREE;
            this.lastReturnTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /** 
    * @Description: 将该对象标记为可销毁的对象 
    * @Param: [] 
    * @return: void 
    * @Author: 薛谌
    * @Date: 2019/9/17 
    */
    @Override
    public synchronized void destory() {
        this.state = PooledObjectState.DESTORYED;
    }

    public long getLastBorrowTime() {
        return lastBorrowTime;
    }

    public long getLastReturnTime() {
        return lastReturnTime;
    }
}
