package impl;

import api.ObjectFactory;
import api.PooledObject;

public class ObjectFactoryImpl<T> implements ObjectFactory<T> {
    @Override
    public PooledObject<T> createObject() throws Exception {
        return null;
    }

    @Override
    public void activateObject(PooledObject<T> p) throws Exception {

    }

    @Override
    public boolean validateObject(PooledObject<T> p) {
        return false;
    }

    @Override
    public void passivateObject(PooledObject<T> p) throws Exception {

    }

    @Override
    public void destroyObject(PooledObject<T> p) throws Exception {

    }
}
