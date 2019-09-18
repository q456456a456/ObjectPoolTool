package api;

import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * 定义用于将普通对象包装成可放入对象池中的对象（PooledObject类型）所需要的工厂的接口。
 * 具体实现可由用户根据实际情况定义自己的工厂。
 */

public interface ObjectFactory<T> {
    /** 创建新的实例，返回PooledObject对象*/
    PooledObject<T> createObject() throws Exception;

    /**在pool借出对象前，需要将其激活，做一些标记以及清空的操作*/
    void activateObject(PooledObject<T> p) throws Exception;

    /**
    1.在pool借出对象前，在activate状态时验证是否有效
    2.在对象被还给pool的时候，验证是否有效
    */
    boolean validateObject(PooledObject<T> p);

    /**在对象被还给pool的时候,进行钝化操作*/
    void passivateObject(PooledObject<T> p) throws Exception;

    /**当实例从pool中丢掉时使用，一般是被验证无效或者是特殊的实现考虑，和对象的状态并没有关系*/
    void destroyObject(PooledObject<T> p) throws Exception;
}
