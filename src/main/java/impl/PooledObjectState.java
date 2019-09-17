package impl;
/**
 * 对象池中对象的状态。
 */
public enum PooledObjectState {
    FREE,          //空闲状态
    USED,          //正在被使用
    RETURNING,    //正在归还中
    DESTORYED;    //销毁
    private PooledObjectState() {
    }
}
