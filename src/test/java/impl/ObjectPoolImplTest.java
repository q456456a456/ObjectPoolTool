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
        ((ObjectPoolImpl<Student>) pool).setMaxWaitTime(5000);
        Student s = pool.borrowObject();
    }

    @Test
    public void threadsBorrowObject() throws Exception{
        ObjectPool<Student> pool = new ObjectPoolImpl<>(new StudentFactory());
        ((ObjectPoolImpl<Student>) pool).setMaxWaitTime(5);
        for(int i=1 ; i<=50 ; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        Student s = pool.borrowObject();
                    }catch (Exception e){

                    }
                }
            });
            t.setName("线程"+i);
            t.start();
        }
        System.out.println(" ");
    }
}

