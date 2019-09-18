package impl;

import api.ObjectFactory;
import api.ObjectPool;
import api.PooledObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjectPoolImplTest {

    @Test
    public void borrowObject() throws Exception{
        ObjectPool<Student> pool = new ObjectPoolImpl<>(new StudentFactory(),5000,10000,8,2,10,10000);
        ((ObjectPoolImpl<Student>) pool).setMaxWaitTime(5000);
        Student s = pool.borrowObject();
        Student s1 = pool.borrowObject();
        Student s2 = pool.borrowObject();
        pool.returnObject(s);
        pool.returnObject(s1);
        pool.returnObject(s2);
        Thread.sleep(20000);
        System.out.println(" ");
    }

    @Test
    public void threadsBorrowObject() throws Exception{
        ObjectPool<Student> pool = new ObjectPoolImpl<>(new StudentFactory(),5000,10000,8,2,10,10000);
        ((ObjectPoolImpl<Student>) pool).setMaxWaitTime(5000);
        for(int i=1 ; i<=50 ; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        Student s = pool.borrowObject();
                        Thread.sleep(1000);
                        pool.returnObject(s);
                    }catch (Exception e){
                        System.out.println(Thread.currentThread().getName()+"："+e.getMessage());
                    }
                }
            });
            t.setName("线程"+i);
            t.start();
        }

        Thread.sleep(30000);
        System.out.println(" ");
    }


}

