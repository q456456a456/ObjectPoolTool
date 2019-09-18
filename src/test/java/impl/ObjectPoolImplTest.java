package impl;

import api.ObjectFactory;
import api.ObjectPool;
import api.PooledObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjectPoolImplTest {

    @Test
    public void borrowObject() throws Exception{
        ObjectPool<Student> pool = new ObjectPoolImpl<>(new StudentFactory());
        Student s = pool.borrowObject();
    }
}

