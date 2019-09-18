package api;
import java.io.Closeable;
/**
 * 对象池的接口。
 */
public interface ObjectPool<T> extends Closeable{

    /** 从对象池中取出（移除）一个对象，返回这个对象的引用*/
    T borrowObject() throws Exception;

    /** 使用完对象之后，将其重新添加到对象池中*/
    void returnObject(T obj) throws Exception;

    /** 销毁某一个对象*/
    void destroyObject(T obj) throws Exception;

    /** 向对象池中添加一个对象*/
    void addObject(T obj) throws Exception;

    /** 关闭对象池*/
    void close();
}
