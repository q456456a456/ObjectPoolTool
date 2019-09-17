package api;

import impl.PooledObjectState;

/**
 * 对象池中对象的接口，需要对放入对象池中的对象进行包装之后再放入。
 * 主要添加对象的状态（空闲、使用、归还等）
 */
public interface PooledObject<T> {
    /** 获取对象本身*/
    T getObject();
    /** 获取对象的状态*/
    PooledObjectState getState();
    /** 有某个线程从对象池中获取并使用该对象*/
    boolean use();
    /** 有某个线程将该对象归还至对象池*/
    boolean giveBack();
    /** 将该对象标记为可销毁的对象*/
    void destory();

}
